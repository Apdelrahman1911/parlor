package com.parlor.core.result

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import kotlin.test.Test

class ResultTest {

    @Test
    fun map_transforms_success() {
        val r: Result<Int, String> = Result.Success(2)
        val mapped = r.map { it * 3 }
        assertThat(mapped).isEqualTo(Result.Success(6))
    }

    @Test
    fun map_preserves_failure() {
        val r: Result<Int, String> = Result.Failure("boom")
        val mapped = r.map { it * 3 }
        assertThat(mapped).isEqualTo(Result.Failure("boom"))
    }

    @Test
    fun flatMap_chains_success() {
        val r: Result<Int, String> = Result.Success(2)
        val chained = r.flatMap { Result.Success(it + 1) }
        assertThat(chained).isEqualTo(Result.Success(3))
    }

    @Test
    fun flatMap_short_circuits_on_failure() {
        val r: Result<Int, String> = Result.Failure("nope")
        val chained = r.flatMap { Result.Success(it + 1) }
        assertThat(chained).isEqualTo(Result.Failure("nope"))
    }

    @Test
    fun mapError_transforms_failure() {
        val r: Result<Int, String> = Result.Failure("x")
        val mapped = r.mapError { it + "y" }
        assertThat(mapped).isEqualTo(Result.Failure("xy"))
    }

    @Test
    fun onSuccess_runs_block_only_on_success() {
        var hit = 0
        val r: Result<Int, String> = Result.Success(7)
        r.onSuccess { hit = it }
        assertThat(hit).isEqualTo(7)
    }

    @Test
    fun onFailure_runs_block_only_on_failure() {
        var captured: String? = null
        val r: Result<Int, String> = Result.Failure("err")
        r.onFailure { captured = it }
        assertThat(captured).isEqualTo("err")
    }

    @Test
    fun getOrNull_returns_data_on_success() {
        val r: Result<Int, String> = Result.Success(5)
        assertThat(r.getOrNull()).isEqualTo(5)
    }

    @Test
    fun getOrNull_returns_null_on_failure() {
        val r: Result<Int, String> = Result.Failure("x")
        assertThat(r.getOrNull()).isNull()
    }

    @Test
    fun getOrElse_falls_back_on_failure() {
        val r: Result<Int, String> = Result.Failure("e")
        assertThat(r.getOrElse { 42 }).isEqualTo(42)
    }
}
