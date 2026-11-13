package io.github.hyunolike.ontime.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
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
 * 14.6인치 화면에서 본문은 3mm, 핵심 정보는 5mm 이상이 되도록 잡았다.
 */
@Immutable
data class OnTimeTypography(
    /** 주행 화면의 여유 숫자. 한 화면에 하나만 쓴다. */
    val display: TextStyle = style(190, 800, 190),
    /** 정차 화면의 여유 숫자. */
    val displaySmall: TextStyle = style(112, 800, 116),
    val headline: TextStyle = style(40, 700, 50),
    val title: TextStyle = style(30, 700, 40),
    val bodyLarge: TextStyle = style(26, 500, 36),
    val body: TextStyle = style(22, 400, 32),
    val label: TextStyle = style(20, 600, 28),
    /** 가장 작은 글자. 이보다 작게 쓰지 않는다. */
    val caption: TextStyle = style(18, 500, 26),
)

/** 숫자가 바뀌어도 자리가 흔들리지 않게 고정폭 숫자를 쓴다. */
private fun style(size: Int, weight: Int, lineHeight: Int) = TextStyle(
    fontFamily = AstaSans,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = "tnum",
)
