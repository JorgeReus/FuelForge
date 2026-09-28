package com.reus.nutri

import java.util.UUID

actual fun newIdentifier(): String = UUID.randomUUID().toString()
