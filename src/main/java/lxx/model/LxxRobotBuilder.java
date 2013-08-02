package lxx.model;import lxx.utils.BattleRules;import lxx.utils.LxxConstants;import lxx.utils.LxxPoint;import lxx.utils.LxxUtils;import lxx.utils.func.Option;import robocode.Rules;import robocode.util.Utils;import java.util.ArrayList;import java.util.Collections;import java.util.List;import static java.lang.Double.NaN;import static java.lang.Math.*;import static lxx.model.LxxRobot.*;public class LxxRobotBuilder {    private final Option<LxxRobot> prev;    private final BattleRules rules;    private final long time;    private final int round;    private Option<String> name = Option.none();    private Option<LxxPoint> position = Option.none();    private Option<Double> velocity = Option.none();    private Option<Double> heading = Option.none();    private Option<Double> energy = Option.none();    private Option<Long> lastScanTime = Option.none();    private Option<Double> radarHeading = Option.none();    private Option<Double> gunHeading = Option.none();    private Option<Boolean> alive = Option.none();    private Option<Double> firePower = Option.none();    private Option<Double> gunHeat = Option.none();    private double returnedEnergy;    private double receivedDmg;    private Option<Boolean> hitRobot = Option.none();    private Option<Double> wallDamage = Option.none();    private List<LxxWave> bulletsInAir;    public LxxRobotBuilder(BattleRules rules, Option<LxxRobot> prev, Long time, int round) {        this.rules = rules;        this.prev = prev;        this.time = time;        this.round = round;        this.name = prev.map(toName);        if (prev.defined()) {            bulletsInAir = new ArrayList<LxxWave>(prev.get().bulletsInAir);        } else {            bulletsInAir = new ArrayList<LxxWave>();        }    }    public LxxRobot build() {        final String name = getName();        final double velocity = getVelocity();        final double heading = getHeading();        final LxxPoint pos = getPosition();        final double energy = this.energy.getOr(rules.initialEnergy);        final long lastScanTime = this.lastScanTime.getOr(prev.map(toLastScanTime).getOr(0L));        final Double radarHeading = this.radarHeading.getNullable();        final Double gunHeading = this.gunHeading.getNullable();        final boolean alive = this.alive.getOr(prev.map(toAlive).getOr(true));        final boolean isPrevStateKnown = isPrevStateKnown();        final double acceleration = calculateAcceleration(prev, velocity);        final double firePower = getFirePower();        final double gunHeat = this.gunHeat.getOr(isPrevStateKnown ? calculateGunHeat(prev.get(), firePower) : rules.initialGunHeat - rules.gunCoolingRate * time);        final double speed = Math.abs(velocity);        final double movementDirection = calculateMovementDirection();        assert !isPrevStateKnown || position.empty() || !prev.get().scanned() || mayHitWall(prev.get()).getOr(false) ||                LxxUtils.isNear(prev.get().position.aDistance(position.get()), prev.get().speed + acceleration);        return new LxxRobot(rules, name, pos, velocity, heading, energy, lastScanTime, time, round, radarHeading, gunHeading,                alive, firePower, gunHeat, speed, acceleration, movementDirection, Collections.unmodifiableList(bulletsInAir));    }    public String getName() {        return this.name.getOr(prev.map(toName).getOr(UNKNOWN));    }    private Double getHeading() {        return this.heading.getOr(prev.map(toHeading).getOr(0D));    }    public LxxPoint getPosition() {        if (position.defined()) {            return position.get();        }        LxxPoint imaginaryPosition = new LxxPoint();        if (prev.defined()) {            imaginaryPosition = prev.get().position;            if (prev.get().alive) {                imaginaryPosition = imaginaryPosition.project(getHeading(), getVelocity());            }        }        return imaginaryPosition;    }    private Double getVelocity() {        return this.velocity.getOr(prev.map(toVelocity).getOr(0D));    }    private boolean isPrevStateKnown() {        return prev.defined() && prev.get().known();    }    public Double getFirePower() {        return this.firePower.getOr(isPrevStateKnown() ? calculateFirePower(prev, calculateAcceleration(prev, getVelocity())) : 0D);    }    private Double calculateGunHeat(LxxRobot prevState, double firePower) {        if (firePower > 0) {            return Rules.getGunHeat(firePower) - rules.gunCoolingRate;        } else {            return max(0, prevState.gunHeat - rules.gunCoolingRate * (time - prevState.time));        }    }    private Double calculateFirePower(Option<LxxRobot> prevStateOption, double acceleration) {        if (energy.empty()) {            return 0D;        }        final LxxRobot prevState = prevStateOption.get();        double expectedEnergy = prevState.energy;        final boolean isHitWall = mayHitWall(prevState).getOr(false) && acceleration < -Rules.DECELERATION;        if (energy.get() != prevState.energy) {            expectedEnergy += returnedEnergy - receivedDmg;            if (isHitWall) {                expectedEnergy -= wallDamage.getOr(Rules.getWallHitDamage(LxxUtils.limit(0, prevState.speed + prevState.acceleration, Rules.MAX_VELOCITY)));            }            if (hitRobot.getOr(false)) {                expectedEnergy -= LxxConstants.ROBOT_HIT_DAMAGE;            }        }        final boolean canFire = prevState.gunHeat - rules.gunCoolingRate * (time - prevState.time) <= 0 && prevState.alive;        if (canFire && energy.get() < expectedEnergy) {            return expectedEnergy - energy.get();        } else {            return 0D;        }    }    public void alive() {        alive = Option.of(true);    }    public void energy(double energy) {        this.energy = Option.of(energy);    }    public void gunHeading(double gunHeadingRadians) {        this.gunHeading = Option.of(gunHeadingRadians);    }    public void heading(double headingRadians) {        this.heading = Option.of(headingRadians);    }    public void position(LxxPoint position) {        this.position = Option.of(position);    }    public void radarHeading(double radarHeadingRadians) {        this.radarHeading = Option.of(radarHeadingRadians);    }    public void velocity(double velocity) {        this.velocity = Option.of(velocity);    }    public void gunHeat(double gunHeat) {        this.gunHeat = Option.of(gunHeat);    }    public void returnedEnergy(double returnedEnergy) {        this.returnedEnergy += returnedEnergy;    }    public void receivedDmg(double receivedDmg) {        this.receivedDmg += receivedDmg;    }    public void name(String name) {        this.name = Option.of(name);    }    public void alive(boolean alive) {        this.alive = Option.of(alive);    }    public void died() {        this.alive = Option.of(false);        this.energy = Option.of(0D);    }    public void hitRobot() {        this.hitRobot = Option.of(true);    }    public void hitWall(double wallHitDamage) {        this.wallDamage = Option.of(wallHitDamage);    }    public void fire(double firePower) {        this.firePower = Option.of(firePower);    }    public void lastScanTime(long lastScanTime) {        this.lastScanTime = Option.of(lastScanTime);    }    public void bulletFired(LxxWave firedBullet) {        bulletsInAir.add(firedBullet);    }    private double calculateAcceleration(Option<LxxRobot> prevStateOption, double velocity) {        if (!isPrevStateKnown()) {            return 0;        }        final LxxRobot prevState = prevStateOption.get();        double acceleration;        if (signum(velocity) == signum(prevState.velocity) || abs(velocity) < 0.001) {            acceleration = abs(velocity) - abs(prevState.velocity);        } else {            acceleration = abs(velocity);        }        if (prevState.time != prevState.lastScanTime) {            acceleration = LxxUtils.limit(-Rules.DECELERATION, acceleration, Rules.ACCELERATION);        }        assert acceleration >= -Rules.MAX_VELOCITY - LxxConstants.EPSILON &&                acceleration <= Rules.ACCELERATION + LxxConstants.EPSILON &&                (acceleration >= -Rules.DECELERATION - LxxConstants.EPSILON ||                        wallDamage.getOr(0D) > 0 || hitRobot.getOr(false) || mayHitWall(prevState).getOr(true) ||                        !prevState.scanned()                )                : String.format("position: %s + velocity: %s + heading: %s -> position: %s + velocity: %s = acceleration: %s",                prevState.position, prevState.velocity, prevState.heading, position, velocity, acceleration);        return acceleration;    }    private Option<Boolean> mayHitWall(LxxRobot prevState) {        if (prevState.position == null || velocity.empty() || position.empty()) {            return Option.none();        }        // rules should be on right side to enforce unboxing instead of autoboxing        if (velocity.get() == 0 &&                (Utils.isNear(rules.field.availableLeftX, position.get().getX()) ||                        Utils.isNear(rules.field.availableRightX, position.get().getX()) ||                        Utils.isNear(rules.field.availableBottomY, position.get().getY()) ||                        Utils.isNear(rules.field.availableTopY, position.get().getY()))) {            return Option.of(true);        }        return Option.of(true);    }    private Double calculateMovementDirection() {        final Double velocity = this.velocity.getOr(0D);        assert !Double.isNaN(velocity);        if (velocity == 0 || heading.empty()) {            return NaN;        } else if (velocity > 0) {            return heading.get();        } else {            return Utils.normalAbsoluteAngle(heading.get() + LxxConstants.RADIANS_180);        }    }    public void bulletGone(LxxWave goneBullet) {        bulletsInAir.remove(goneBullet);    }}