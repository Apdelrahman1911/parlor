package com.parlor.games.wordimpostor.di

import com.parlor.games.wordimpostor.WordImpostorDefinition
import org.koin.dsl.module

val wordImpostorModule = module {
    single { WordImpostorDefinition() }
}
