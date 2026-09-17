package com.parlor.games.ghamza.di

import com.parlor.games.ghamza.GhamzaDefinition
import org.koin.dsl.module

val ghamzaModule = module {
    single { GhamzaDefinition() }
}
