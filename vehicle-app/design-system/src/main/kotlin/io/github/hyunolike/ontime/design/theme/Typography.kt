package io.github.hyunolike.ontime.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hyunolike.ontime.design.R

/** Pleos Connect의 지정 서체인 Asta Sans(SIL OFL 1.1). 가변 서체 하나에서 굵기를 뽑아 쓴다. */
@OptIn(ExperimentalTextApi::class)
private fun asta(weight: Int) = Font(
    R.font.asta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val AstaSans = FontFamily(asta(400), asta(500), asta(600), asta(700), asta(800))

/**
 * 글자 크기 단계. 기준 화면(가로 1280dp)에서의 값이고 화면 크기에 따라 함께 커진다.
 * 14.6인치 화면에서 가장 작은 글자가 3mm, 주행 중 읽는 글자가 5mm 이상이 되도록 잡았다.
 * 주행 화면에서는 display, title, bodyLarge만 쓴다.
 */
@Immutable
data class OnTimeTypography(
    /** 지금 이야기하는 곳의 이름. 한 화면에 하나만 쓴다. */
    val display: TextStyle = style(120, 800, 128, tight = true),
    /** 이름이 길 때, 그리고 정차 화면의 큰 제목. */
    val displaySmall: TextStyle = style(76, 800, 86, tight = true),
    val headline: TextStyle = style(44, 700, 54),
    val title: TextStyle = style(32, 700, 42),
    /** 주행 중 읽는 문장의 최소 크기. */
    val bodyLarge: TextStyle = style(28, 500, 40),
    val body: TextStyle = style(24, 400, 34),
    val label: TextStyle = style(20, 600, 28),
    /** 가장 작은 글자. 이보다 작게 쓰지 않는다. */
    val caption: TextStyle = style(18, 500, 26),
)

/** 숫자가 바뀌어도 자리가 흔들리지 않게 고정폭 숫자를 쓴다. 큰 글자는 자간을 조금 좁힌다. */
private fun style(size: Int, weight: Int, lineHeight: Int, tight: Boolean = false) = TextStyle(
    fontFamily = AstaSans,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = if (tight) (-0.02).em else 0.em,
    fontFeatureSettings = "tnum",
)
