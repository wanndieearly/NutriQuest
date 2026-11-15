import io
import json
import unittest
from unittest.mock import patch
from urllib.error import URLError

from fastapi import HTTPException

from main import FoodEntry, MealEntry, RoastRequest, roast


class RoastTest(unittest.TestCase):
    def test_sends_adaptive_prompt_and_parses_bilingual_comment(self):
        comment = "香蕉今日好搶鏡，main character energy!"
        payload = {"message": {"content": json.dumps({"comment": comment})}}
        meals = [MealEntry(foods=[FoodEntry(name="Banana", servings=1)])]
        with patch("main.urlopen", return_value=io.BytesIO(json.dumps(payload).encode())) as upstream:
            result = roast(RoastRequest(scope="day", meals=meals))
        self.assertEqual(result.comment, comment)
        request = json.loads(upstream.call_args.args[0].data)
        self.assertIn("gentle and welcoming", request["messages"][0]["content"])
        self.assertIn("day as a whole", request["messages"][0]["content"])
        self.assertEqual(json.loads(request["messages"][1]["content"])["meals"][0]["foods"][0]["name"], "Banana")

    def test_offline_ollama_returns_a_retryable_error(self):
        meals = [MealEntry(foods=[FoodEntry(name="Banana", servings=1)])]
        with patch("main.urlopen", side_effect=URLError("offline")), self.assertRaises(HTTPException) as error:
            roast(RoastRequest(scope="meal", meals=meals))
        self.assertEqual(error.exception.status_code, 503)
