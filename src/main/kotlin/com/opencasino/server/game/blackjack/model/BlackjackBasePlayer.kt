package com.opencasino.server.game.blackjack.model

import com.opencasino.server.game.Updatable
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.model.PlayerEntity
import com.opencasino.server.network.pack.InitPack
import com.opencasino.server.network.pack.PrivateUpdatePack
import com.opencasino.server.network.pack.UpdatePack
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.pack.update.IPrivateUpdatePackProvider
import com.opencasino.server.service.shared.BlackjackDecision
import kotlin.properties.Delegates

abstract class BlackjackBasePlayer<GR, IP : InitPack, UP : UpdatePack, PUP : PrivateUpdatePack>(
    id: Long, gameRoom: GR,
    var userSession: PlayerSession
) : PlayerEntity<Long, GR, IP, UP>(id, gameRoom), Updatable, IPrivateUpdatePackProvider<PUP> {

    var balance by Delegates.notNull<Double>()

    lateinit var lastDecision: BlackjackDecision

    lateinit var playerDeck: CardDeck

    /*var movingState: MutableMap<BlackjackDecision, Boolean> = Arrays.stream(BlackjackDecision.entries.toTypedArray()).collect(
        Collectors.toMap(
            { decision: BlackjackDecision -> decision },
            { false }
        )
    )*/
}