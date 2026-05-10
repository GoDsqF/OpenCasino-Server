package com.opencasino.server.user

import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.util.UUID

@Table(name = "user_oauth_identities")
data class UserOAuthIdentity(

    @Column("user_id")
    val userId: UUID,

    @Column("provider")
    val provider: String,

    @Column("subject")
    val subject: String,
)
