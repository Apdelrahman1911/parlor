package com.parlor.games.dominoes.di

import com.parlor.games.dominoes.DominoDefinition
import org.koin.dsl.module

val dominoModule = module {
    single { DominoDefinition() }
}
