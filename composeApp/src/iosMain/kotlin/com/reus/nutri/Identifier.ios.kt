package com.reus.nutri

import platform.Foundation.NSUUID

actual fun newIdentifier(): String = NSUUID().UUIDString
