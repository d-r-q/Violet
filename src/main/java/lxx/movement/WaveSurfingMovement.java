package lxx.movement;

import lxx.Violet;
import lxx.model.BattleState;
import lxx.model.LxxRobot;
import lxx.model.LxxWave;
import lxx.movement.orbital.AvoidEnemyOrbitalMovement;
import lxx.movement.orbital.OrbitDirection;
import lxx.paint.Canvas;
import lxx.paint.Circle;
import lxx.paint.Text;
import lxx.services.DangerService;
import lxx.services.WaveDangerInfo;
import lxx.utils.func.F3;
import robocode.Rules;

import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static java.lang.Math.min;
import static java.lang.Math.signum;
import static lxx.utils.LxxUtils.List;

public class WaveSurfingMovement {

    public static final MovementOptionDangerComparator optionsComparator = new MovementOptionDangerComparator();

    private final DangerService dangerService;
    private final AvoidEnemyOrbitalMovement orbitalMovement;

    private OrbitDirection lastOrbitDirection;
    private Color pathColor;

    public WaveSurfingMovement(DangerService dangerService, AvoidEnemyOrbitalMovement orbitalMovement) {
        this.dangerService = dangerService;
        this.orbitalMovement = orbitalMovement;
    }

    public MovementDecision getMovementDecision(BattleState bs) {
        final List<LxxWave> enemyBullets = bs.getEnemyBullets(bs.me, bs.enemy.alive ? 2 : 0, 2);
        if (bs.enemy.alive && enemyBullets.size() < 2) {
            enemyBullets.add(new LxxWave(bs.enemy, bs.me, Rules.getBulletSpeed(3), bs.time + 1));
        }

        pathColor = Violet.primaryColor155;
        lastOrbitDirection = selectOrbitDirection(bs.me, bs.me, bs.enemy, enemyBullets, lastOrbitDirection).orbitDirection;

        return orbitalMovement.getMovementDecision(bs.me, enemyBullets.get(0), lastOrbitDirection, bs.enemy);
    }

    private MovementOption selectOrbitDirection(LxxRobot myRealState, LxxRobot me, LxxRobot enemy, List<LxxWave> waves,
                                                OrbitDirection lastOrbitDirection) {
        assert waves != null && waves.size() <= 2 : waves;

        final LxxWave firstWave;
        final LxxWave secondWave;
        final int firstWaveFlightTimeLimit;
        if (waves.size() == 1) {
            firstWave = waves.get(0);
            secondWave = null;
            firstWaveFlightTimeLimit = 0;
        } else {
            if (waves.get(0).getFlightTime(me) < waves.get(1).getFlightTime(me)) {
                firstWave = waves.get(0);
                secondWave = waves.get(1);
            } else {
                firstWave = waves.get(1);
                secondWave = waves.get(0);
            }
            firstWaveFlightTimeLimit = 2;
        }

        final DangerFunction firstWaveSameDirDF = new DangerFunction(dangerService.getWaveDangerInfo(firstWave), myRealState, 0.98);
        final DangerFunction firstWaveAnotherDirDF = new DangerFunction(dangerService.getWaveDangerInfo(firstWave), myRealState, 1);
        final MovementOption[] options = new MovementOption[]{
                predict(firstWave, me, enemy, firstWaveFlightTimeLimit, OrbitDirection.CLOCKWISE,
                        lastOrbitDirection == OrbitDirection.CLOCKWISE ? firstWaveSameDirDF : firstWaveAnotherDirDF),

                predict(firstWave, me, enemy, firstWaveFlightTimeLimit, OrbitDirection.STOP,
                        lastOrbitDirection == OrbitDirection.STOP ? firstWaveSameDirDF : firstWaveAnotherDirDF),

                predict(firstWave, me, enemy, firstWaveFlightTimeLimit, OrbitDirection.COUNTER_CLOCKWISE,
                        lastOrbitDirection == OrbitDirection.COUNTER_CLOCKWISE ? firstWaveSameDirDF : firstWaveAnotherDirDF)
        };

        pathColor = Violet.secondaryColor155;

        return selectBestOption(myRealState, enemy, secondWave, options);
    }

    private MovementOption selectBestOption(LxxRobot myRealState, LxxRobot enemy, LxxWave secondWave, MovementOption[] options) {
        Arrays.sort(options, optionsComparator);

        if (secondWave == null) {
            return options[0];
        }

        final MovementOption[] secondWaveOptions = new MovementOption[3];
        secondWaveOptions[0] = selectOrbitDirection(myRealState, options[0].me, enemy, List(secondWave), null);

        if (options[0].danger + secondWaveOptions[0].danger < options[1].danger) {
            return options[0];
        }

        int bestOptIdx = 0;
        for (int i = 1; i < secondWaveOptions.length; i++) {
            secondWaveOptions[i] = selectOrbitDirection(myRealState, options[i].me, enemy, List(secondWave), null);
            if (options[i].danger + secondWaveOptions[i].danger < options[bestOptIdx].danger + secondWaveOptions[bestOptIdx].danger) {
                bestOptIdx = i;
            }
        }

        return options[bestOptIdx];
    }

    private MovementOption predict(LxxWave wave, LxxRobot me, LxxRobot enemy, int flightLimit,
                                   OrbitDirection orbitDirection, DangerFunction dangerFunction) {
        final MovementDecision enemyMd = new MovementDecision(
                enemy != null ? Rules.MAX_VELOCITY * signum(enemy.velocity) : 0, 0);

        double minDist = enemy != null ? me.distance(enemy) : Integer.MAX_VALUE;

        do {
            final MovementDecision md = orbitalMovement.getMovementDecision(me, wave, orbitDirection, enemy);
            me = new LxxRobot(me, md.turnRate, md.desiredVelocity);
            if (enemy != null && enemy.alive) {
                enemy = new LxxRobot(enemy, enemyMd.turnRate, enemyMd.desiredVelocity);
                assert enemy.alive;
                minDist = min(minDist, me.distance(enemy));
            }

            if (Canvas.WS.enabled()) {
                Canvas.WS.draw(new Circle(me, 3, true), pathColor);
                Canvas.WS.draw(new Circle(enemy, 3, true), pathColor);
            }
        } while ((wave.distance(me) - (me.time - wave.time) * wave.speed) / wave.speed > flightLimit);


        return new MovementOption(orbitDirection, dangerFunction.f(me, wave, minDist), me);
    }

    private static double getDistDanger(double distBetween) {
        if (distBetween < 50) {
            return 500 / distBetween;
        } if (distBetween < 400) {
            return 400 / (400 + Math.pow(Math.E, distBetween / 40)) + 0.01;
        } else if (distBetween < 1000) {
            return (1000 - distBetween) / 600 * 0.01;
        } else {
            return 0;
        }
    }

    private static class MovementOption {

        public final OrbitDirection orbitDirection;
        public final double danger;
        public final LxxRobot me;

        private MovementOption(OrbitDirection orbitDirection, double danger, LxxRobot me) {
            this.orbitDirection = orbitDirection;
            this.danger = danger;
            this.me = me;
        }
    }

    private static class DangerFunction implements F3<LxxRobot, LxxWave, Double, Double> {

        private final WaveDangerInfo waveDangerInfo;
        private final LxxRobot myRealState;
        private final double mult;

        private DangerFunction(WaveDangerInfo waveDangerInfo, LxxRobot myRealState, double mult) {
            this.waveDangerInfo = waveDangerInfo;
            this.myRealState = myRealState;
            this.mult = mult;
        }

        @Override
        public Double f(LxxRobot me, LxxWave wave, Double minDist) {
            final double distDng = getDistDanger(minDist);
            final double pointDanger = waveDangerInfo.getPointDanger(me);
            final double flightTime = wave.getFlightTime(myRealState.position, myRealState.time);
            if (Canvas.WS.enabled()) {
                Canvas.WS.draw(
                        new Text(List("distDng=" + distDng, "pntDng=" + pointDanger, "ft=" + flightTime),
                                me.x() + 10, me.y() + 10), Color.RED);
            }
            return (pointDanger / flightTime + 4 * distDng) * mult;
        }
    }

    private static class MovementOptionDangerComparator implements Comparator<MovementOption> {

        @Override
        public int compare(MovementOption o1, MovementOption o2) {
            return (int) signum(o1.danger - o2.danger);
        }
    }

}