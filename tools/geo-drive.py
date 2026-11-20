#!/usr/bin/env python3
"""
에뮬레이터에 송파 시연 경로의 좌표를 흘려 넣어 차가 달리는 것처럼 만든다.

길벗 pleos 빌드를 실제 위치 모드로 바꾸면 앱은 FusedLocation SDK로 이 좌표를 받아 움직인다.
내비는 에뮬레이터 GPS를 받지 않으므로(NMEA 체크섬 문제) 내비 화면의 차는 그대로 서 있다.

경유지: 앱 로그(OnTimePleos)에 `-> addWaypoint <이름>`이 나오면 경로에서 그곳과 가장 가까운 지점까지 달린 뒤
그 장소로 들어가 선다. `-> removeWaypoint`가 나오면 경로로 돌아와 이어 달린다.

    adb shell am broadcast -a io.github.hyunolike.ontime.POC --es poc location-live
    python3 tools/geo-drive.py            # 앱에서 "송파 코스 출발"을 누른 뒤 실행

경로와 장소 좌표는 vehicle-app의 ScenarioSongpa.kt에서 읽는다. 경로 © OpenStreetMap 기여자(ODbL).
"""
import argparse
import math
import os
import re
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCENARIO = ROOT / "vehicle-app/platform-fake/src/main/kotlin/io/github/hyunolike/ontime/fake/ScenarioSongpa.kt"


def adb_path():
    home = os.environ.get("ANDROID_HOME") or str(Path.home() / "Library/Android/sdk")
    candidate = Path(home) / "platform-tools/adb"
    return str(candidate) if candidate.exists() else "adb"


def load_scenario():
    text = SCENARIO.read_text(encoding="utf-8")
    shape_block = re.search(r"SHAPE\s*=\s*((?:\s*\"[^\"]*\"\s*\+?)+)", text).group(1)
    shape = "".join(re.findall(r"\"([^\"]*)\"", shape_block)).replace("\\\\", "\\")
    places = {
        name: (float(lat), float(lon))
        for name, lat, lon in re.findall(r'Destination\("songpa-[a-z-]+", "([^"]+)", ([0-9.]+), ([0-9.]+)', text)
    }
    return decode(shape), places


def decode(encoded):
    points, index, lat, lon = [], 0, 0, 0
    while index < len(encoded):
        values = []
        for _ in range(2):
            result = shift = 0
            while True:
                chunk = ord(encoded[index]) - 63
                index += 1
                result |= (chunk & 0x1F) << shift
                shift += 5
                if chunk < 0x20:
                    break
            values.append(~(result >> 1) if result & 1 else result >> 1)
        lat += values[0]
        lon += values[1]
        points.append((lat / 1e5, lon / 1e5))
    return points


class Route:
    """평면 미터로 펼친 경로. 출발점 기준 등장방형 투영이다(앱의 TrackPlayer와 같은 방식)."""

    def __init__(self, points):
        self.lat0, self.lon0 = points[0]
        self.my = math.radians(1) * 6_371_000
        self.mx = self.my * math.cos(math.radians(self.lat0))
        self.xy = [((lon - self.lon0) * self.mx, (lat - self.lat0) * self.my) for lat, lon in points]
        self.cum = [0.0]
        for (x1, y1), (x2, y2) in zip(self.xy, self.xy[1:]):
            self.cum.append(self.cum[-1] + math.hypot(x2 - x1, y2 - y1))
        self.length = self.cum[-1]

    def at(self, meters):
        meters = max(0.0, min(meters, self.length))
        for i in range(len(self.cum) - 1):
            if self.cum[i + 1] >= meters:
                seg = self.cum[i + 1] - self.cum[i] or 1
                t = (meters - self.cum[i]) / seg
                x = self.xy[i][0] + t * (self.xy[i + 1][0] - self.xy[i][0])
                y = self.xy[i][1] + t * (self.xy[i + 1][1] - self.xy[i][1])
                return self.to_latlon(x, y)
        return self.to_latlon(*self.xy[-1])

    def to_latlon(self, x, y):
        return self.lat0 + y / self.my, self.lon0 + x / self.mx

    def nearest(self, lat, lon):
        px, py = (lon - self.lon0) * self.mx, (lat - self.lat0) * self.my
        best, best_at = float("inf"), 0.0
        for i in range(len(self.xy) - 1):
            (x1, y1), (x2, y2) = self.xy[i], self.xy[i + 1]
            dx, dy = x2 - x1, y2 - y1
            l2 = dx * dx + dy * dy or 1
            t = max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / l2))
            d = math.hypot(x1 + t * dx - px, y1 + t * dy - py)
            if d < best:
                best, best_at = d, self.cum[i] + t * (self.cum[i + 1] - self.cum[i])
        return best_at


class Waypoints(threading.Thread):
    """앱 로그를 읽어 경유지 추가와 해제를 알려 준다."""

    def __init__(self, adb):
        super().__init__(daemon=True)
        self.adb = adb
        self.added = None
        self.removed = threading.Event()
        self.lock = threading.Lock()

    def run(self):
        # 스크립트를 띄우기 전에 넣은 경유지(출발 전 "여기로 안내")도 이어받는다. 마지막 위치 모드 전환 뒤의 기록만 본다.
        history = subprocess.run([self.adb, "logcat", "-d", "-s", "OnTimePleos"], stdout=subprocess.PIPE, text=True).stdout
        pending = None
        for line in history.splitlines():
            if "-> location" in line:
                pending = None
            added = re.search(r"-> addWaypoint (.+?)\s*$", line)
            if added:
                pending = added.group(1)
            elif "-> removeWaypoint" in line:
                pending = None
        if pending:
            self.handle(f"-> addWaypoint {pending}")
        proc = subprocess.Popen([self.adb, "logcat", "-T", "1", "-s", "OnTimePleos"], stdout=subprocess.PIPE, text=True)
        for line in proc.stdout:
            self.handle(line)

    def handle(self, line):
        added = re.search(r"-> addWaypoint (.+?)\s*$", line)
        if added:
            with self.lock:
                self.added = added.group(1)
                self.removed.clear()
            print(f"[경유지] {self.added}")
        elif "-> removeWaypoint" in line:
            self.removed.set()
            print("[경유지 해제]")

    def take(self):
        with self.lock:
            name, self.added = self.added, None
            return name


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--speed", type=float, default=100.0, help="초속(m). 기본 100은 초속 10m의 10배속으로 앱 재생과 같다")
    parser.add_argument("--interval", type=float, default=0.5, help="좌표를 보내는 간격(초)")
    parser.add_argument("--hold", type=float, default=10.0, help="끝에 닿은 뒤 서 있는 시간(초)")
    parser.add_argument("--warmup", type=float, default=3.0, help="달리기 전에 출발점에 서 있는 시간(초). 남아 있던 위치를 먼저 바로잡는다")
    args = parser.parse_args()

    # 앞서 띄운 스크립트가 남아 있으면 서로 다른 좌표를 보내게 된다. 먼저 멈춘다.
    others = subprocess.run(["pgrep", "-f", "geo-drive.py"], stdout=subprocess.PIPE, text=True).stdout.split()
    for pid in others:
        if int(pid) != os.getpid():
            subprocess.run(["kill", pid], check=False)

    adb = adb_path()
    points, places = load_scenario()
    route = Route(points)
    step = args.speed * args.interval
    waypoints = Waypoints(adb)
    waypoints.start()

    def send(lat, lon):
        # geo fix는 경도, 위도 순서다.
        subprocess.run([adb, "emu", "geo", "fix", f"{lon:.6f}", f"{lat:.6f}"], stdout=subprocess.DEVNULL, check=False)
        time.sleep(args.interval)

    def go_straight(a, b):
        distance = math.hypot((b[1] - a[1]) * route.mx, (b[0] - a[0]) * route.my)
        n = max(1, int(distance / step))
        for k in range(1, n + 1):
            send(a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n)

    print(f"경로 {route.length:.0f}m, 초속 {args.speed:.0f}m, {args.interval}초 간격")
    # 에뮬레이터에는 지난번 위치가 남아 있다. 출발점에 먼저 서서 위치를 맞춘 뒤 달린다.
    for _ in range(int(args.warmup / args.interval)):
        send(*route.at(0))
    traveled, detour_at, detour_name = 0.0, None, None
    while traveled < route.length:
        name = waypoints.take()
        if name and name in places:
            detour_name, detour_at = name, route.nearest(*places[name])
            print(f"  {detour_name}: 경로 {detour_at:.0f}m 지점에서 들어갑니다")
        if detour_at is not None and traveled + step >= detour_at >= traveled:
            traveled = detour_at
            exit_point = route.at(traveled)
            send(*exit_point)
            go_straight(exit_point, places[detour_name])
            print(f"  {detour_name}에 섰습니다. 앱에서 다시 출발하면 이어 갑니다")
            while not waypoints.removed.is_set():
                send(*places[detour_name])
            go_straight(places[detour_name], exit_point)
            detour_at, detour_name = None, None
            continue
        send(*route.at(traveled))
        traveled += step
    end = route.at(route.length)
    print("목적지에 닿았습니다")
    for _ in range(int(args.hold / args.interval)):
        send(*end)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(0)
