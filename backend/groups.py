"""
F8 — Groups and leaderboard server (Joe)

A small REST API that runs on your Mac during development and the demo.
Phones / emulators upload each day's score; friends in the same group see a
daily or weekly ranking with a crown for the leader, streaks and emoji
reactions. Only scores are shared: never weight, body data, photos or meals.

Data is stored in a SQLite file (backend/nutriquest_groups.db by default;
set NUTRIQUEST_DB to change it).

Run (from the backend folder):
    pip install -r requirements.txt
    uvicorn main:app --host 0.0.0.0 --port 8000
Emulators reach the Mac at http://10.0.2.2:8000.
"""

import os
import secrets
import sqlite3
import uuid
from contextlib import contextmanager
from datetime import date as Date, datetime, timedelta, timezone
from typing import Annotated, Literal, Optional

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field, StringConstraints

router = APIRouter(tags=["groups"])

DB_PATH = os.environ.get("NUTRIQUEST_DB", os.path.join(os.path.dirname(os.path.abspath(__file__)), "nutriquest_groups.db"))
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   # no 0/O or 1/I, so codes are easy to read out
REACTIONS = ["👏", "🔥", "💪", "🥦", "😂", "😬"]

UserId = Annotated[str, StringConstraints(strip_whitespace=True, min_length=8, max_length=64, pattern=r"^[A-Za-z0-9-]+$")]
Nickname = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=30)]
GroupName = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=40)]
JoinCode = Annotated[str, StringConstraints(strip_whitespace=True, to_upper=True, min_length=6, max_length=6)]


# ---------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    nickname TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS groups (
    id TEXT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS members (
    group_id TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id),
    joined_at TEXT NOT NULL,
    PRIMARY KEY (group_id, user_id)
);
CREATE TABLE IF NOT EXISTS scores (
    user_id TEXT NOT NULL REFERENCES users(id),
    day TEXT NOT NULL,
    score INTEGER NOT NULL,
    meal_count INTEGER NOT NULL,
    calorie_score INTEGER,
    macro_score INTEGER,
    meal_fit INTEGER,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (user_id, day)
);
CREATE TABLE IF NOT EXISTS reactions (
    group_id TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    day TEXT NOT NULL,
    from_user TEXT NOT NULL,
    to_user TEXT NOT NULL,
    emoji TEXT NOT NULL,
    PRIMARY KEY (group_id, day, from_user, to_user)
);
"""


@contextmanager
def db():
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    try:
        connection.executescript(SCHEMA)
        yield connection
        connection.commit()
    finally:
        connection.close()


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_day(value: str) -> Date:
    try:
        return Date.fromisoformat(value)
    except ValueError:
        raise HTTPException(422, "Dates must look like 2026-11-16.")


def require_user(connection, user_id: str):
    row = connection.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
    if row is None:
        raise HTTPException(404, "Unknown user. Set your nickname first.")
    return row


def require_member(connection, group_id: str, user_id: str):
    row = connection.execute("SELECT 1 FROM members WHERE group_id = ? AND user_id = ?", (group_id, user_id)).fetchone()
    if row is None:
        raise HTTPException(403, "You're not in this group.")


def group_json(connection, row) -> dict:
    count = connection.execute("SELECT COUNT(*) FROM members WHERE group_id = ?", (row["id"],)).fetchone()[0]
    return {"groupId": row["id"], "code": row["code"], "name": row["name"], "memberCount": count}


# ---------------------------------------------------------------------------
# Request bodies
# ---------------------------------------------------------------------------

class UserBody(BaseModel):
    nickname: Nickname


class CreateGroupBody(BaseModel):
    userId: UserId
    name: GroupName


class JoinGroupBody(BaseModel):
    userId: UserId
    code: JoinCode


class LeaveGroupBody(BaseModel):
    userId: UserId


class ScoreBody(BaseModel):
    userId: UserId
    date: str
    score: int = Field(ge=0, le=100)
    mealCount: int = Field(ge=1, le=50)
    calorieScore: Optional[int] = Field(default=None, ge=0, le=100)
    macroScore: Optional[int] = Field(default=None, ge=0, le=100)
    mealFit: Optional[int] = Field(default=None, ge=0, le=100)


class ReactionBody(BaseModel):
    userId: UserId
    toUserId: UserId
    date: str
    emoji: str


# ---------------------------------------------------------------------------
# Users
# ---------------------------------------------------------------------------

@router.get("/health")
def health():
    return {"ok": True, "service": "nutriquest-groups"}


@router.put("/users/{user_id}")
def upsert_user(user_id: UserId, body: UserBody):
    """Registers this phone (its random user ID) or changes its nickname."""
    with db() as c:
        c.execute(
            "INSERT INTO users (id, nickname, created_at) VALUES (?, ?, ?) "
            "ON CONFLICT(id) DO UPDATE SET nickname = excluded.nickname",
            (user_id, body.nickname, now()),
        )
    return {"userId": user_id, "nickname": body.nickname}


# ---------------------------------------------------------------------------
# Groups
# ---------------------------------------------------------------------------

@router.post("/groups")
def create_group(body: CreateGroupBody):
    with db() as c:
        require_user(c, body.userId)
        group_id = str(uuid.uuid4())
        for _ in range(20):
            code = "".join(secrets.choice(CODE_ALPHABET) for _ in range(6))
            try:
                c.execute(
                    "INSERT INTO groups (id, code, name, created_by, created_at) VALUES (?, ?, ?, ?, ?)",
                    (group_id, code, body.name, body.userId, now()),
                )
                break
            except sqlite3.IntegrityError:
                continue
        else:
            raise HTTPException(500, "Couldn't create a unique join code. Try again.")
        c.execute("INSERT INTO members (group_id, user_id, joined_at) VALUES (?, ?, ?)", (group_id, body.userId, now()))
        return group_json(c, c.execute("SELECT * FROM groups WHERE id = ?", (group_id,)).fetchone())


@router.post("/groups/join")
def join_group(body: JoinGroupBody):
    with db() as c:
        require_user(c, body.userId)
        row = c.execute("SELECT * FROM groups WHERE code = ?", (body.code,)).fetchone()
        if row is None:
            raise HTTPException(404, "No group has that code. Check it and try again.")
        c.execute(
            "INSERT OR IGNORE INTO members (group_id, user_id, joined_at) VALUES (?, ?, ?)",
            (row["id"], body.userId, now()),
        )
        return group_json(c, row)


@router.post("/groups/{group_id}/leave")
def leave_group(group_id: str, body: LeaveGroupBody):
    with db() as c:
        c.execute("DELETE FROM members WHERE group_id = ? AND user_id = ?", (group_id, body.userId))
        left = c.execute("SELECT COUNT(*) FROM members WHERE group_id = ?", (group_id,)).fetchone()[0]
        if left == 0:
            c.execute("DELETE FROM groups WHERE id = ?", (group_id,))
    return {"ok": True}


@router.get("/users/{user_id}/groups")
def my_groups(user_id: UserId):
    with db() as c:
        rows = c.execute(
            "SELECT g.* FROM groups g JOIN members m ON m.group_id = g.id WHERE m.user_id = ? ORDER BY m.joined_at",
            (user_id,),
        ).fetchall()
        return {"groups": [group_json(c, r) for r in rows]}


# ---------------------------------------------------------------------------
# Scores
# ---------------------------------------------------------------------------

@router.put("/scores")
def upload_score(body: ScoreBody):
    """Saves (or replaces) one user's score for one day."""
    day = parse_day(body.date)
    with db() as c:
        require_user(c, body.userId)
        c.execute(
            "INSERT INTO scores (user_id, day, score, meal_count, calorie_score, macro_score, meal_fit, updated_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(user_id, day) DO UPDATE SET "
            "score = excluded.score, meal_count = excluded.meal_count, calorie_score = excluded.calorie_score, "
            "macro_score = excluded.macro_score, meal_fit = excluded.meal_fit, updated_at = excluded.updated_at",
            (body.userId, day.isoformat(), body.score, body.mealCount, body.calorieScore, body.macroScore, body.mealFit, now()),
        )
    return {"ok": True}


@router.delete("/scores/{user_id}/{day}")
def delete_score(user_id: UserId, day: str):
    """Removes a day's score (e.g. all of that day's meals were deleted)."""
    with db() as c:
        c.execute("DELETE FROM scores WHERE user_id = ? AND day = ?", (user_id, parse_day(day).isoformat()))
    return {"ok": True}


# ---------------------------------------------------------------------------
# Reactions
# ---------------------------------------------------------------------------

@router.put("/groups/{group_id}/reactions")
def react(group_id: str, body: ReactionBody):
    """One reaction per person, per friend, per day. Sending the same emoji again removes it."""
    if body.emoji not in REACTIONS:
        raise HTTPException(422, f"Reaction must be one of {' '.join(REACTIONS)}")
    if body.userId == body.toUserId:
        raise HTTPException(422, "You can't react to yourself.")
    day = parse_day(body.date).isoformat()
    with db() as c:
        require_member(c, group_id, body.userId)
        require_member(c, group_id, body.toUserId)
        existing = c.execute(
            "SELECT emoji FROM reactions WHERE group_id = ? AND day = ? AND from_user = ? AND to_user = ?",
            (group_id, day, body.userId, body.toUserId),
        ).fetchone()
        if existing is not None and existing["emoji"] == body.emoji:
            c.execute(
                "DELETE FROM reactions WHERE group_id = ? AND day = ? AND from_user = ? AND to_user = ?",
                (group_id, day, body.userId, body.toUserId),
            )
            return {"emoji": None}
        c.execute(
            "INSERT INTO reactions (group_id, day, from_user, to_user, emoji) VALUES (?, ?, ?, ?, ?) "
            "ON CONFLICT(group_id, day, from_user, to_user) DO UPDATE SET emoji = excluded.emoji",
            (group_id, day, body.userId, body.toUserId, body.emoji),
        )
    return {"emoji": body.emoji}


# ---------------------------------------------------------------------------
# Leaderboard
# ---------------------------------------------------------------------------

def streak_for(connection, user_id: str, day: Date) -> int:
    """Days in a row with a score, ending on `day` (or the day before, if today has no score yet)."""
    rows = connection.execute(
        "SELECT day FROM scores WHERE user_id = ? AND day <= ? ORDER BY day DESC LIMIT 400", (user_id, day.isoformat())
    ).fetchall()
    days = {Date.fromisoformat(r["day"]) for r in rows}
    cursor = day if day in days else day - timedelta(days=1)
    count = 0
    while cursor in days:
        count += 1
        cursor -= timedelta(days=1)
    return count


@router.get("/groups/{group_id}/leaderboard")
def leaderboard(
    group_id: str,
    userId: UserId,
    date: str,
    period: Literal["day", "week"] = Query("day"),
):
    """
    period=day:  ranks members by their score on `date`.
    period=week: ranks by the average daily score from Monday to `date` (the week resets every Monday).
    Members with no score are listed last. Ties share a rank. The leader gets the crown.
    """
    day = parse_day(date)
    start = day if period == "day" else day - timedelta(days=day.weekday())
    with db() as c:
        group = c.execute("SELECT * FROM groups WHERE id = ?", (group_id,)).fetchone()
        if group is None:
            raise HTTPException(404, "That group no longer exists.")
        require_member(c, group_id, userId)
        members = c.execute(
            "SELECT u.id, u.nickname FROM members m JOIN users u ON u.id = m.user_id WHERE m.group_id = ? ORDER BY m.joined_at",
            (group_id,),
        ).fetchall()

        entries = []
        for member in members:
            rows = c.execute(
                "SELECT score, meal_count FROM scores WHERE user_id = ? AND day BETWEEN ? AND ?",
                (member["id"], start.isoformat(), day.isoformat()),
            ).fetchall()
            score = round(sum(r["score"] for r in rows) / len(rows)) if rows else None
            reactions = c.execute(
                "SELECT emoji, COUNT(*) AS n FROM reactions WHERE group_id = ? AND day = ? AND to_user = ? GROUP BY emoji",
                (group_id, day.isoformat(), member["id"]),
            ).fetchall()
            mine = c.execute(
                "SELECT emoji FROM reactions WHERE group_id = ? AND day = ? AND from_user = ? AND to_user = ?",
                (group_id, day.isoformat(), userId, member["id"]),
            ).fetchone()
            entries.append({
                "userId": member["id"],
                "nickname": member["nickname"],
                "score": score,
                "daysScored": len(rows),
                "mealCount": sum(r["meal_count"] for r in rows),
                "streak": streak_for(c, member["id"], day),
                "reactions": {r["emoji"]: r["n"] for r in reactions},
                "myReaction": mine["emoji"] if mine else None,
            })

        entries.sort(key=lambda e: (e["score"] is None, -(e["score"] or 0), e["nickname"].casefold()))
        rank, previous = 0, object()
        for index, entry in enumerate(entries):
            if entry["score"] is None:
                entry["rank"] = None
            else:
                if entry["score"] != previous:
                    rank = index + 1
                    previous = entry["score"]
                entry["rank"] = rank
            entry["crown"] = entry["rank"] == 1

        return {
            "groupId": group["id"],
            "name": group["name"],
            "code": group["code"],
            "period": period,
            "date": day.isoformat(),
            "start": start.isoformat(),
            "reactions": REACTIONS,
            "members": entries,
        }
