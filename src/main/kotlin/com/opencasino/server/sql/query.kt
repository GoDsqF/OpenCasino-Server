package com.opencasino.server.sql

const val INSERT_PLAYER = """\
    INSERT INTO players (id, username, email, first_name, last_name, created_at)
    VALUES (:id, :username, :email, :firstName, :lastName, :createdAt);
    """

const val UPDATE_BALANCE = """
    UPDATE players SET balance = :balance, updated_at = :updatedAt
    WHERE id = :id;
"""

const val SET_USERHASH = """
    UPDATE players SET user_hash = :hash, updated_at = :updatedAt
    WHERE id = :id;
"""

const val GET_PLAYER_BY_ID = """
    SELECT * FROM players WHERE id = :id;
"""