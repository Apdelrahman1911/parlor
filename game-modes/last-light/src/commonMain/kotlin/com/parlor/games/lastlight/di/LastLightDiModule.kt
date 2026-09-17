package com.parlor.games.lastlight.di

import com.parlor.games.lastlight.LastLightDefinition
import org.koin.dsl.module

val lastLightModule = module {
    single { LastLightDefinition(get()) }
}
