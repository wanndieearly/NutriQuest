"""F8 server tests. Run from the backend folder: python -m unittest test_groups -v"""

import os
import tempfile
import unittest

from fastapi.testclient import TestClient

import groups
from main import app


class GroupsTest(unittest.TestCase):
    def setUp(self):
        handle, self.db_path = tempfile.mkstemp(suffix=".db")
        os.close(handle)
        groups.DB_PATH = self.db_path
        self.client = TestClient(app)
        self.alex = self.register("alex-0000-0001", "Alex")
        self.amy = self.register("amy-0000-0002", "Amy")
        self.sam = self.register("sam-0000-0003", "Sam")

    def tearDown(self):
        os.remove(self.db_path)

    # helpers
    def register(self, user_id, nickname):
        response = self.client.put(f"/users/{user_id}", json={"nickname": nickname})
        self.assertEqual(response.status_code, 200, response.text)
        return user_id

    def create(self, user_id, name="Hall 5 gym crew"):
        response = self.client.post("/groups", json={"userId": user_id, "name": name})
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    def join(self, user_id, code):
        return self.client.post("/groups/join", json={"userId": user_id, "code": code})

    def score(self, user_id, day, value, meals=3):
        response = self.client.put("/scores", json={"userId": user_id, "date": day, "score": value, "mealCount": meals})
        self.assertEqual(response.status_code, 200, response.text)

    def board(self, group_id, user_id, day, period="day"):
        response = self.client.get(f"/groups/{group_id}/leaderboard", params={"userId": user_id, "date": day, "period": period})
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    # tests
    def test_health(self):
        self.assertTrue(self.client.get("/health").json()["ok"])

    def test_create_and_join_by_code(self):
        group = self.create(self.alex)
        self.assertEqual(len(group["code"]), 6)
        self.assertEqual(group["memberCount"], 1)
        joined = self.join(self.amy, group["code"].lower())   # codes are case-insensitive
        self.assertEqual(joined.status_code, 200)
        self.assertEqual(joined.json()["memberCount"], 2)
        mine = self.client.get(f"/users/{self.amy}/groups").json()["groups"]
        self.assertEqual([g["name"] for g in mine], ["Hall 5 gym crew"])

    def test_wrong_code_is_rejected(self):
        self.assertEqual(self.join(self.amy, "ZZZZZZ").status_code, 404)

    def test_unknown_user_cannot_create_a_group(self):
        response = self.client.post("/groups", json={"userId": "nobody-123456", "name": "x"})
        self.assertEqual(response.status_code, 404)

    def test_daily_ranking_with_crown_and_people_without_scores_last(self):
        group = self.create(self.alex)
        self.join(self.amy, group["code"])
        self.join(self.sam, group["code"])
        self.score(self.alex, "2026-11-16", 78)
        self.score(self.amy, "2026-11-16", 91)
        board = self.board(group["groupId"], self.alex, "2026-11-16")
        names = [(m["nickname"], m["score"], m["rank"], m["crown"]) for m in board["members"]]
        self.assertEqual(names, [("Amy", 91, 1, True), ("Alex", 78, 2, False), ("Sam", None, None, False)])

    def test_ties_share_a_rank(self):
        group = self.create(self.alex)
        self.join(self.amy, group["code"])
        self.score(self.alex, "2026-11-16", 80)
        self.score(self.amy, "2026-11-16", 80)
        ranks = [m["rank"] for m in self.board(group["groupId"], self.alex, "2026-11-16")["members"]]
        self.assertEqual(ranks, [1, 1])

    def test_uploading_again_replaces_the_score(self):
        group = self.create(self.alex)
        self.score(self.alex, "2026-11-16", 40, meals=1)
        self.score(self.alex, "2026-11-16", 85, meals=3)
        me = self.board(group["groupId"], self.alex, "2026-11-16")["members"][0]
        self.assertEqual((me["score"], me["mealCount"]), (85, 3))

    def test_deleting_a_score(self):
        group = self.create(self.alex)
        self.score(self.alex, "2026-11-16", 85)
        self.client.delete(f"/scores/{self.alex}/2026-11-16")
        self.assertIsNone(self.board(group["groupId"], self.alex, "2026-11-16")["members"][0]["score"])

    def test_weekly_average_resets_on_monday(self):
        group = self.create(self.alex)
        self.score(self.alex, "2026-11-15", 10)   # Sunday of the previous week: not counted
        self.score(self.alex, "2026-11-16", 80)   # Monday
        self.score(self.alex, "2026-11-17", 90)   # Tuesday
        me = self.board(group["groupId"], self.alex, "2026-11-17", period="week")["members"][0]
        self.assertEqual((me["score"], me["daysScored"]), (85, 2))

    def test_streak_counts_days_in_a_row(self):
        group = self.create(self.alex)
        for day in ["2026-11-13", "2026-11-14", "2026-11-15"]:
            self.score(self.alex, day, 70)
        # Today (16th) has no score yet, so the streak still counts up to yesterday
        self.assertEqual(self.board(group["groupId"], self.alex, "2026-11-16")["members"][0]["streak"], 3)
        self.score(self.alex, "2026-11-16", 70)
        self.assertEqual(self.board(group["groupId"], self.alex, "2026-11-16")["members"][0]["streak"], 4)

    def test_reactions_toggle_and_are_counted(self):
        group = self.create(self.alex)
        self.join(self.amy, group["code"])
        url = f"/groups/{group['groupId']}/reactions"
        body = {"userId": self.alex, "toUserId": self.amy, "date": "2026-11-16", "emoji": "🔥"}
        self.assertEqual(self.client.put(url, json=body).json()["emoji"], "🔥")
        amy = [m for m in self.board(group["groupId"], self.alex, "2026-11-16")["members"] if m["nickname"] == "Amy"][0]
        self.assertEqual((amy["reactions"], amy["myReaction"]), ({"🔥": 1}, "🔥"))
        self.assertIsNone(self.client.put(url, json=body).json()["emoji"])   # same emoji again removes it

    def test_cannot_react_to_yourself_or_outside_the_group(self):
        group = self.create(self.alex)
        url = f"/groups/{group['groupId']}/reactions"
        self.assertEqual(self.client.put(url, json={"userId": self.alex, "toUserId": self.alex, "date": "2026-11-16", "emoji": "🔥"}).status_code, 422)
        self.assertEqual(self.client.put(url, json={"userId": self.alex, "toUserId": self.sam, "date": "2026-11-16", "emoji": "🔥"}).status_code, 403)

    def test_non_members_cannot_see_the_leaderboard(self):
        group = self.create(self.alex)
        response = self.client.get(f"/groups/{group['groupId']}/leaderboard", params={"userId": self.sam, "date": "2026-11-16"})
        self.assertEqual(response.status_code, 403)

    def test_last_person_leaving_deletes_the_group(self):
        group = self.create(self.alex)
        self.client.post(f"/groups/{group['groupId']}/leave", json={"userId": self.alex})
        self.assertEqual(self.join(self.amy, group["code"]).status_code, 404)

    def test_scores_are_validated(self):
        response = self.client.put("/scores", json={"userId": self.alex, "date": "2026-11-16", "score": 150, "mealCount": 1})
        self.assertEqual(response.status_code, 422)
        response = self.client.put("/scores", json={"userId": self.alex, "date": "16/11/2026", "score": 50, "mealCount": 1})
        self.assertEqual(response.status_code, 422)


if __name__ == "__main__":
    unittest.main()
