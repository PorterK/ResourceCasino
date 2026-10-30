# ResourceCasino

A Paper plugin for **Minecraft 26.3**: a casino that runs on real resources. Deposit **copper, iron, emeralds, diamonds or netherite**, play **Roulette, Slots, Horse Racing, Plinko or Spin the Wheel**, and withdraw whatever you have left. Like a real casino, every game gives the house a small edge, so over time the house wins.

## Download

**[⬇ ResourceCasino.jar (latest build)](https://github.com/PorterK/ResourceCasino/releases/download/latest/ResourceCasino.jar)**

Drop it into your server's `plugins/` folder and restart. You need Paper 26.3 running on Java 25. Then, as an op, run `/casino build` once to build the casino floor.

## How it works

1. **Cashier.** Open `/casino` → Cashier. Click any copper, iron, emerald, diamond or netherite in your own inventory to deposit the stack (blocks count as 9), or hit *Deposit everything*. Click a resource at the top to withdraw it as ingots or gems.
2. **Pick a resource and a bet** in the bar along the bottom of any game. Each resource is its own currency: bet diamonds, win diamonds.
3. **Play.** Winnings land straight back in your casino balance.

## The games

| Game | How to play | Average paid back |
|---|---|---|
| **Roulette** | European single-zero table. Drop chips on numbers (35 to 1), red/black, odd/even, 1-18/19-36 (1 to 1) or dozens (2 to 1), then spin. | 97.3% |
| **Slots** | Three reels. Three netherite pays 150x, diamonds 50x, emeralds 25x... any three ingots 2x, two berries 3x, one berry gives your stake back. | 95.3% |
| **Horse Racing** | Five horses, one lap, fresh odds every race (favourite ~2.6x to long shot ~11x). Back one and watch it run. | ~90% |
| **Plinko** | Drop a ball through 8 rows of pegs into 9 bins: 12x, 3x, 1.2x, 0.7x, 0.4x, 0.7x, 1.2x, 3x, 12x. | 95.9% |
| **Spin the Wheel** | 24 segments: one 10x jackpot, then 3x, 2x, 1.5x, 1x and 15 blanks. | 95.8% |

`/casino odds` prints these in game. Payouts always round down.

## The casino floor

`/casino build` creates a separate void world (`resourcecasino`) with an 80×80 hall built by code, where the games happen for real:

- **Racetrack**: an oval dirt track with five lanes, a grass infield, curbs, a checkered finish line and a grandstand. Each race started from the Horse Racing screen (bet at the lecterns by the stands) runs live: five saddled horses in their lane colours gallop one lap, exactly the race the screen shows.
- **Roulette wheel**: a real wheel set into the floor with 37 numbered pockets. On a spin the wheel turns one way while the ball slides round the rim the other way, slows, rattles down off the rim and drops into the winning pocket. Click the rim to bet.
- **Plinko board**: a tall pegboard on the west wall, 8 rows of pegs over 9 coloured bins. Balls bounce peg to peg; up to 8 can be in play at once.
- **The big wheel**: a giant glowing wheel on the east wall. Spins from the wheel screen turn it, and it stops on the same segment.
- **Cashier** booth with a cashier villager (right-click it), a row of nine **slot machines**, a lobby kiosk in the middle and an exit by the entrance

Each physical game shows one round at a time (Plinko up to 8). If it's busy, or you're playing from somewhere else, the chest screen still shows your result.

`/casino tp` takes you there and `/casino leave` brings you back to where you were. Nobody takes damage or gets hungry on the floor, and only admins in creative mode can change the build. Rebuilding is safe: it replaces the hall in the casino world and nothing else.

## Commands

| Command | Permission | Default |
|---|---|---|
| `/casino` opens the casino menu | `resourcecasino.play` | everyone |
| `/casino cashier\|roulette\|slots\|horses\|plinko\|wheel` opens one screen | `resourcecasino.play` | everyone |
| `/casino tp` / `/casino leave` | `resourcecasino.play` | everyone |
| `/casino balance`, `/casino odds` | `resourcecasino.play` | everyone |
| `/casino build` builds or rebuilds the casino floor | `resourcecasino.admin` | op |
| `/casino stats` shows what the house has won so far | `resourcecasino.admin` | op |
| `/casino reload` reloads config.yml | `resourcecasino.admin` | op |

`/rc` works as well as `/casino`.

## Configuration

`plugins/ResourceCasino/config.yml`:

```yaml
play-anywhere: true          # false = games only open at their tables on the casino floor
world-name: resourcecasino
keep-casino-loaded: true
max-bet: { copper: 256, iron: 128, emerald: 64, diamond: 32, netherite: 8 }
announce-big-wins: 10        # broadcast wins of 10x the stake or more (0 = never)
horse-racing:
  house-edge: 0.10           # odds pay 90% of fair
  race-seconds: 12           # one lap
wheel:
  segments: [10, 0, 1, 0, 2, 0, 1.5, 0, 0, 1, 0, 3, 0, 1.5, 0, 0, 2, 0, 1, 0, 0, 0, 0, 0]
  spin-seconds: 6
```

The wheel needs exactly 24 segments averaging **under 1**. If you set a wheel the house would lose on, the plugin refuses it and uses the default.

Balances are saved in `plugins/ResourceCasino/data.yml` every minute and on shutdown. If a spin is still animating when the server stops, it's paid out first.

## Building

```sh
./gradlew build    # needs JDK 25 → build/libs/ResourceCasino-<version>.jar
```

GitHub Actions builds every push and tests it on a live Paper 26.3 server. The test builds the casino floor, checks every station, runs 300,000 rounds of each game to confirm the house edge, spins the big wheel and the roulette wheel, runs a horse race and drops Plinko balls. Pushes to `main` update the **latest** release. Pushing a tag like `v1.0.1` creates a versioned release.
