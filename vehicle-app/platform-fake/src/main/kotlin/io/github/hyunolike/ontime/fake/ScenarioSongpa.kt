package io.github.hyunolike.ontime.fake

import io.github.hyunolike.ontime.fake.track.Polyline
import io.github.hyunolike.ontime.fake.track.Track
import io.github.hyunolike.ontime.platform.Destination
import io.github.hyunolike.ontime.platform.Poi
import io.github.hyunolike.ontime.platform.Side
import io.github.hyunolike.ontime.slack.Source

/**
 * 판교에서 송파의 한성백제박물관으로 가는 길. 서울공항을 지나 석촌동 고분군과 방이동 고분군 곁을 지난다.
 *
 * 출발점은 Pleos Connect 에뮬레이터 내비의 차 위치(성남 수정구 시흥동)다. 내비 경로(13.0km, 송파대로와 방이역,
 * 몽촌토성역 방면)와 같은 길을 지나도록 골랐다.
 *
 * 경로 모양과 회전은 OSRM(router.project-osrm.org, 2026-11-20 조회) 결과이고, 장소 좌표는 OpenStreetMap
 * Nominatim에서 찾았다. © OpenStreetMap 기여자, ODbL.
 *
 * 해설은 널리 알려진 사실을 짧게 줄인 예시 문장이고, 들르는 데 드는 시간은 예시 값이다.
 * 왼쪽, 오른쪽은 해설 앞에 자동으로 붙으므로 문장에 넣지 않는다.
 */
object ScenarioSongpa {
    val HANSEONG = Destination("songpa-hanseong", "한성백제박물관", 37.5154492, 127.1210850, "서울 송파구 위례성대로 71")
    val SEOUL_AIRBASE = Destination("songpa-seoul-airbase", "서울공항", 37.4445568, 127.1104831, "경기 성남시 수정구")
    val SEOKCHON = Destination("songpa-seokchon", "석촌동 고분군", 37.5024456, 127.1022937, "서울 송파구 석촌동")
    val BANGI = Destination("songpa-bangi", "방이동 고분군", 37.5074183, 127.1192072, "서울 송파구 방이동")
    val MONGCHON = Destination("songpa-mongchon", "몽촌토성", 37.5175329, 127.1126700, "서울 송파구 방이동, 올림픽공원")

    val pois = listOf(
        Poi(
            SEOUL_AIRBASE,
            "이름은 서울공항이지만 성남에 있는 공군 비행장이에요. 대통령 전용기가 뜨고 내리는 곳이죠.",
            canStop = false,
            source = Source.REPLAY,
        ),
        Poi(
            SEOKCHON,
            // 경로에서 1.2km 떨어져 창밖으로 보이지 않을 수 있다. "보인다"고 하지 않는다.
            "가까이에 백제 초기의 돌무지무덤이 모여 있어요. 돌을 계단처럼 쌓아 올린 모양이 고구려 무덤과 닮았죠. " +
                "가장 큰 3호분은 한 변이 50m 가까이 돼요.",
            canStop = true,
            source = Source.REPLAY,
            highlight = "계단처럼 쌓은 3호분 둘러보기",
            stayMinutes = 30,
            extraMinutes = 40,
        ),
        Poi(
            BANGI,
            "잔디로 덮인 둥근 무덤들이 공원 안에 남아 있어요. 돌로 방을 만들고 흙을 덮은 무덤이에요. " +
                "백제 무덤으로 알려졌지만 신라 무덤이라는 견해도 있어요.",
            canStop = true,
            source = Source.REPLAY,
            highlight = "잔디 언덕 사이 산책",
            stayMinutes = 20,
            extraMinutes = 25,
        ),
        // 목적지 바로 옆이라 길가에서 소개하고, 차를 세운 뒤 걸어서 둘러볼 곳으로도 쓴다.
        Poi(
            MONGCHON,
            "흙을 쌓아 만든 백제의 성이에요. 지금은 올림픽공원 안에서 성벽 위 산책로로 걸어 볼 수 있어요.",
            canStop = false,
            source = Source.REPLAY,
            highlight = "성벽 위 산책로 걷기",
            shortStory = "흙으로 쌓은 백제의 성, 몽촌토성이에요.",
        ),
    )

    /** OSRM polyline(정밀도 5). 14,293m. */
    private const val SHAPE =
        "odjcFwdvfW?E@G@IBOBE@GHMHONWd@s@^g@KWISEQGSG]GUIYGYG]Om@Mi@EYCQCOAS?IAOAQ?I?O?YAKBYD[F]DUBIHOBGBIJYN" +
        "c@Tw@Pu@e@M}Ak@k@U}CqAwFwBa@OeBk@q@Qs@Qw@Qg@O_Ci@KC[IkAWy@QaAIeAC_A@_ADuCZ_@Fo@F{@Da@BG@Y@e@@{@?gABW" +
        "AkBMyAGqCMuCO]CcAE_AEIAgBFcBPI@o@JI@oDf@]DmAJ{A?uACaBSc@O]MoAe@eA[e@OoFaB_@Gs@M_C]yAMkF_@cE[uAMyBMgC" +
        "B}ADa@Bg@FiBRqBPaFCgB?}ADgDJ}@C}BOoAI{AMo@Ii@GcEc@u@Ma@KiCq@yC_AMEk@Se@Qo@WwAi@o@QoDiAeFwAiCg@kC[wBO" +
        "{Fm@}@Ma@EQAMCsCYy@I[CcGe@KAaAM]Ca@E{Gc@o@E_@CmDW}Fi@m@CIASAKAa@CiCQqAKkBMi@G?w@?w@EiACu@IcBCk@a@}Ei" +
        "@qGSwB[_Gc@qEY{CQmAIc@OcA_@oAIUm@sAYs@o@yAgCeGMYOa@u@aBSa@e@eAGQeDgIc@_A{@uBcA_CaB{Dk@wAQm@M_@Gi@_@C" +
        "eBGyOc@gFQgBE_@Aa@@A@A?G?e@@G?]BcBLo@Jm@Ng@RgFtB_A\\i@Xw@X{B|@OFkKfEm@Vy@ZyAh@cAb@wCnAgGdCYNc@Ni@^gA^" +
        "}@\\u@Vq@Ta@Nw@ZuAh@k@Tc@Vk@Va@Lu@^m@RqAh@eCjAoAn@qBlAq@b@o@d@m@`@CH]TWTa@^wAhAMNcBvA]ZcCrB{@p@SRoAhA" +
        "cAx@cCpB{@x@uCdCKHKHQLM]EMWq@y@iAeB_E_@y@_AkBy@cBMUsByEAEQa@I[MKuAmCmEgGgCiD}AaBgAcAqA{@oMcHcAi@qLoG" +
        "MIeG_DmDqBcCmAa@Sg@YWt@{@fCs@~BgFtPQp@a@xAIZs@lCeBvG"

    val track = Track(
        destination = HANSEONG,
        points = Polyline.decode(SHAPE),
        maneuvers = listOf(
            RouteManeuver(112, "좌회전 창업로", Side.LEFT),
            RouteManeuver(500, "좌회전 대왕판교로", Side.LEFT),
            RouteManeuver(6_437, "우회전 헌릉로", Side.RIGHT),
            RouteManeuver(6_885, "오른쪽 길 헌릉로"),
            RouteManeuver(8_228, "좌회전 송파대로", Side.LEFT),
            RouteManeuver(11_219, "오른쪽 길 송파대로"),
            RouteManeuver(11_526, "우회전 양재대로", Side.RIGHT),
            RouteManeuver(13_540, "좌회전 위례성대로", Side.LEFT),
        ),
    )
}
