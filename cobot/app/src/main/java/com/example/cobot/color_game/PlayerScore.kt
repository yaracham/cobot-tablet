package com.example.cobot.color_game

import kotlinx.serialization.Serializable

@Serializable
data class PlayerScore(
    val name: String,
    val score: Int
)
