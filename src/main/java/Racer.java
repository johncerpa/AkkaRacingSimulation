import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import java.io.Serializable;
import java.util.Random;

public class Racer extends AbstractBehavior<Racer.Command> {
    private final double defaultAverageSpeed = 48.2;
    private int averageSpeedAdjustmentFactor;
    private Random random;
    private double currentSpeed = 0;

    private double getMaxSpeed() {
        return defaultAverageSpeed * (1 + ((double) averageSpeedAdjustmentFactor / 100));
    }

    private double getDistancedMovedPerSecond() {
        return currentSpeed * 1000 / 3600;
    }

    private void determineNextSpeed(int currentPosition, int raceLength) {
        if (currentPosition < ((double) raceLength / 4)) {
            currentSpeed += (((getMaxSpeed() - currentSpeed) / 10) * random.nextDouble());
        } else {
            currentSpeed *= (0.5 * random.nextDouble());
        }

        if (currentSpeed > getMaxSpeed()) {
            currentSpeed = getMaxSpeed();
        }

        if (currentSpeed < 5) {
            currentSpeed = 5;
        }

        if (currentPosition > ((double) raceLength / 2) && currentSpeed < getMaxSpeed() / 2) {
            currentSpeed = getMaxSpeed() / 2;
        }
    }

    private Racer(ActorContext<Command> context) {
        super(context);
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(Racer::new);
    }

    /*
        Receive processes an incoming message and returns the next behavior.
        The next behaviors process the next message and they can change over time, to have a different behavior
    */
    @Override
    public Receive<Command> createReceive() {
        return notYetStarted();
    }

    public Receive<Command> notYetStarted() {
        return newReceiveBuilder()
                .onMessage(StartCommand.class, message -> {
                    this.random = new Random();
                    this.averageSpeedAdjustmentFactor = random.nextInt(30) - 10;
                    return running(message.getRaceLength(), 0);
                })
                .onMessage(PositionCommand.class, message -> {
                    message.getController().tell(new RaceController.RacerUpdateCommand(getContext().getSelf(), 0));
                    return Behaviors.same();
                })
                .build();
    }

    public Receive<Command> running(int raceLength, final int currentPosition) {
        return newReceiveBuilder()
                .onMessage(PositionCommand.class, message -> {
                    determineNextSpeed(raceLength, currentPosition);
                    int newPosition = (int) (currentPosition + getDistancedMovedPerSecond());

                    if (newPosition > raceLength)
                        newPosition = raceLength;

                    if (newPosition == raceLength)
                        return completed(raceLength);

                    return running(raceLength, newPosition);
                })
                .build();
    }

    public Receive<Command> completed(int raceLength) {
        return newReceiveBuilder()
                .onMessage(PositionCommand.class, message -> {
                    message.getController().tell(new RaceController.RacerUpdateCommand(getContext().getSelf(), raceLength));
                    message.getController().tell(new RaceController.RacerFinishedCommand(getContext().getSelf()));
                    return waitingToStop();
                })
                .build();
    }

    public Receive<Command> waitingToStop() {
        return newReceiveBuilder()
                .onSignal(PostStop.class, signal -> {
                    System.out.println("I am about to terminate!");
                    return Behaviors.same();
                })
                .onAnyMessage(message -> Behaviors.same())
                .build();
    }

    public interface Command extends Serializable {}

    public static class StartCommand implements Command {
        private static final long serialVersionUID = 1L;
        private final int raceLength;

        public StartCommand(int raceLength) {
            this.raceLength = raceLength;
        }

        public int getRaceLength() {
            return raceLength;
        }
    }

    public static class PositionCommand implements Command {
        private static final long serialVersionUID = 1L;
        private ActorRef<RaceController.Command> controller;

        public PositionCommand(ActorRef<RaceController.Command> controller) {
            this.controller = controller;
        }

        public ActorRef<RaceController.Command> getController() {
            return controller;
        }
    }
}
