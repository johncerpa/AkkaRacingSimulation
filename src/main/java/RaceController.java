import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class RaceController extends AbstractBehavior<RaceController.Command> {

    private Map<ActorRef<Racer.Command>, Integer> currentPositions;
    private Map<ActorRef<Racer.Command>, Long> finishingTimes;
    private long start;
    private final int raceLength = 100;
    private int displayLength = 40;
    private final int NUMBER_OF_RACERS = 10;
    private Object TIMER_KEY;

    private RaceController(ActorContext<Command> context) {
        super(context);
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(RaceController::new);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(StartCommand.class, message -> {
                    start = System.currentTimeMillis();
                    currentPositions = new HashMap<>();
                    finishingTimes = new HashMap<>();

                    for (int i = 0; i < NUMBER_OF_RACERS; i++) {
                        ActorRef<Racer.Command> racer = getContext().spawn(Racer.create(), "racer" + i);
                        currentPositions.put(racer, 0);
                        racer.tell(new Racer.StartCommand(raceLength));
                    }

                    /*
                        Akka does not use Thread.sleep(), it has a TimerScheduler
                        It sends messages to the current actor at a given interval, so InterruptedExceptions are not possible
                    */
                    return Behaviors.withTimers(timer -> {
                        timer.startTimerAtFixedRate(TIMER_KEY, new GetPositionsCommand(), Duration.ofSeconds(1));
                        return Behaviors.same();
                    });
                })
                .onMessage(GetPositionsCommand.class, message -> {
                    for (ActorRef<Racer.Command> racer : currentPositions.keySet()) {
                        racer.tell(new Racer.PositionCommand(getContext().getSelf()));
                        displayRace();
                    }
                    return Behaviors.same();
                })
                .onMessage(RacerUpdateCommand.class, message -> {
                    currentPositions.put(message.getRacer(), message.getPosition());
                    return Behaviors.same();
                })
                .onMessage(RacerFinishedCommand.class, message -> {
                    finishingTimes.put(message.getRacer(), System.currentTimeMillis());

                    if (finishingTimes.size() == NUMBER_OF_RACERS) return raceCompleteMessageHandler();

                    return Behaviors.same();
                })
                .build();
    }

    public Receive<RaceController.Command> raceCompleteMessageHandler() {
        return newReceiveBuilder()
                .onMessage(GetPositionsCommand.class, message -> {
                    /*for (ActorRef<Racer.Command> racer : currentPositions.keySet()) {
                        getContext().stop(racer);
                    }*/
                    // Shutting down actors manually is not necessary, shutting down the system does the job

                    displayResults();

                    return Behaviors.withTimers(timer -> {
                        timer.cancelAll();
                        return Behaviors.stopped(); // Stops RaceController actor after shutting down all children racers
                    });
                })
                .build();
    }

    private void displayResults() {
        System.out.println("Results");
        finishingTimes.values().stream().sorted().forEach(it -> {
            for (ActorRef<Racer.Command> key : finishingTimes.keySet()) {
                if (finishingTimes.get(key) == it) {
                    String racerId = key.path().toString().substring(key.path().toString().length() - 1);
                    System.out.println("Racer " + racerId + " finished in " + ((double) it - start) / 1000 + " seconds");
                }
            }
        });
    }

    private void displayRace() {
        for (int i = 0; i < 50; i++) System.out.println();

        System.out.println("Race has been running for " + ((System.currentTimeMillis() - start) / 1000) + " seconds");

        System.out.println("    " + new String(new char[displayLength]).replace('\0', '='));

        int i = 0;
        for (ActorRef<Racer.Command> racer : currentPositions.keySet()) {
            System.out.println(i + " : " + new String(new char[currentPositions.get(racer) * displayLength / 100]).replace('\0', '*'));
            i++;
        }
    }

    public interface Command extends Serializable {}

    public static class StartCommand implements Command {
        private static final long serialVersionUID = 1L;
    }

    public static class RacerUpdateCommand implements Command {
        private static final long serialVersionUID = 1L;
        private ActorRef<Racer.Command> racer;
        private int position;

        public RacerUpdateCommand(ActorRef<Racer.Command> racer, int position) {
            this.racer = racer;
            this.position = position;
        }

        public ActorRef<Racer.Command> getRacer() {
            return racer;
        }

        public int getPosition() {
            return position;
        }
    }

    /* This class will always be used internally */
    private static class GetPositionsCommand implements Command {
        private static final long serialVersionUID = 1L;
    }

    public static class RacerFinishedCommand implements Command {
        private static final long serialVersionUID = 1L;
        private ActorRef<Racer.Command> racer;

        public RacerFinishedCommand(ActorRef<Racer.Command> racer) {
            this.racer = racer;
        }

        public ActorRef<Racer.Command> getRacer() {
            return racer;
        }
    }

}
