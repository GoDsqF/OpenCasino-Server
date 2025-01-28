package com.opencasino.server.service.shared

import com.opencasino.server.game.model.User
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : R2dbcRepository<User, UUID>