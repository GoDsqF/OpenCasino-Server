package com.opencasino.server.game.model

import com.opencasino.server.game.Updatable
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.network.pack.InitPack
import com.opencasino.server.network.pack.PrivateUpdatePack
import com.opencasino.server.network.pack.UpdatePack
import com.opencasino.server.network.pack.update.IPrivateUpdatePackProvider
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.shared.BlackjackDecision
import java.util.*
import java.util.stream.Collectors
import kotlin.properties.Delegates

abstract class BasePlayer<GR : GameRoom, IP : InitPack, UP : UpdatePack, PUP : PrivateUpdatePack>(
    id: Long, gameRoom: GR,
    var playerSession: PlayerSession
) : PlayerEntity<Long, GR, IP, UP>(id, gameRoom), Updatable, IPrivateUpdatePackProvider<PUP> {

    var balance by Delegates.notNull<Double>()

    lateinit var decision: BlackjackDecision

    lateinit var playerDeck: MutableList<Card>

    var movingState: MutableMap<BlackjackDecision, Boolean>? = Arrays.stream(BlackjackDecision.entries.toTypedArray()).collect(
        Collectors.toMap(
            { decision: BlackjackDecision -> decision },
            { false }
        )
    )
}