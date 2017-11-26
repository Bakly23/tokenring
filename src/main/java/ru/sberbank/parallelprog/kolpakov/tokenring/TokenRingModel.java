package ru.sberbank.parallelprog.kolpakov.tokenring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ru.sberbank.parallelprog.kolpakov.tokenring.Constants.MAC_ADDRESS_START;

public class TokenRingModel {
    private final static Logger logger = LoggerFactory.getLogger(TokenRingModel.class.getName());
    private final static Random rand = new Random();

    private final List<Node> nodes;
    private final ExecutorService executorService;
    private final int messagesTimeout;
    private final int numberOfMessagesToSend;
    private final Map<Frame, Long> sendTimes;

    public TokenRingModel(int numberOfNodes, int messagesTimeout, int numberOfMessagesToSend) {
        this.executorService = Executors.newFixedThreadPool(numberOfNodes);
        this.messagesTimeout = messagesTimeout;
        this.numberOfMessagesToSend = numberOfMessagesToSend;
        this.sendTimes = new ConcurrentHashMap<>(numberOfMessagesToSend);
        Consumer<Frame> timeMeasuringConsumer =
                frame -> sendTimes.put(frame, System.nanoTime() - frame.getCreationNanoTime());
        this.nodes = IntStream.range(0, numberOfNodes)
                .mapToObj(this::generateAddress)
                .map(macAddress -> new Node(macAddress, timeMeasuringConsumer, null))
                .collect(Collectors.toCollection(LinkedList::new));
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            node.setNextNode(nodes.get((i + 1) % nodes.size()));
            executorService.submit(node.getQueueTask());
        }
        if (logger.isDebugEnabled()) {
            logger.debug("=== Ordering of nodes: ");
            nodes.forEach(node -> logger.debug(node.toString()));
        }
    }

    public static void main(String[] args) throws InterruptedException {
        //warming
        logger.info("Warming has been started.");
        int numberOfWarmingRuns = 3;
        for (int i = 0; i < numberOfWarmingRuns; i++) {
            logger.info("Warming run " + (i + 1) + "/" + numberOfWarmingRuns + " has been started.");
            TokenRingModel model = new TokenRingModel(1000, 0, 500);
            model.run();
        }

        logger.info("=== ===");
        logger.info("=== ===");

        logger.info("Measuring runs have been started.");
        List<Integer> numbersOfNodes = Arrays.asList(10, 20, 50, 100, 200, 500, 1000, 2000);
        List<Double> shareOfMessages = Arrays.asList(0.2, 0.4, 0.6, 0.8, 0.9);
        List<String> resultLines = new ArrayList<>();
        resultLines.add("nnumber of nodes;number of messages;avg throughput, messages/ns;avg latency, ns");
        for (Integer numberOfNodes : numbersOfNodes) {
            shareOfMessages.stream()
                    .map(shareOfMessage -> (int) (shareOfMessage * numberOfNodes))
                    .forEach(numberOfMessages -> {
                        int numberOfLaunches = 10;
                        List<Double> avgLatencies = new ArrayList<>(numberOfMessages * numberOfLaunches);
                        List<Double> throughputs = new ArrayList<>(numberOfLaunches);
                        logger.info("=== RUN: number of nodes {}; number of messages {}", numberOfNodes, numberOfMessages);
                        for (int i = 0; i < numberOfLaunches; i++) {
                            logger.info("launch {}/{}", i + 1, numberOfLaunches);
                            TokenRingModel model = new TokenRingModel(numberOfNodes, 0, numberOfMessages);
                            try {
                                long start = System.nanoTime();
                                Map<Frame, Long> map = model.run();
                                long finish = System.nanoTime();
                                throughputs.add(1_000_000.0 * numberOfMessages / (finish - start));
                                assert map.size() == numberOfMessages;
                                avgLatencies.add(map.values().stream().collect(Collectors.averagingLong(l -> l)));
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        Double avgThroughput = throughputs.stream().collect(Collectors.averagingDouble(d -> d));
                        Double avgLatency = avgLatencies.stream().collect(Collectors.averagingDouble(d -> d));
                        String csvResultString = numberOfNodes
                                + ";"
                                + numberOfMessages
                                + ";"
                                + String.format("%.8f", avgThroughput)
                                + ";"
                                + String.format("%.8f", avgLatency);
                        resultLines.add(csvResultString);
                    });
        }
        try {
            Files.write(
                    Paths.get("calc.csv"),
                    resultLines.stream().collect(Collectors.joining("\n")).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<Frame, Long> run() throws InterruptedException {
        for (int i = 0; i < numberOfMessagesToSend; i++) {
            int fromIndex = rand.nextInt(nodes.size());
            int toIndex = rand.nextInt(nodes.size());
            Node sender = nodes.get(rand.nextInt(nodes.size()));
            Node receiver;
            while ((receiver = nodes.get(toIndex)) == sender) {
                toIndex = rand.nextInt(nodes.size());
            }
            int distance = toIndex > fromIndex
                    ? toIndex - fromIndex
                    : nodes.size() + toIndex - fromIndex;
            Frame frame = new Frame(receiver.getAddress(),
                    sender.getAddress(),
                    ByteBuffer.allocate(4).putInt(rand.nextInt()).array(),
                    distance);
            sender.enqueueFrame(frame);
            if (logger.isDebugEnabled()) {
                logger.debug("Frame with data {} was sent from {} to {}", frame.getData(), sender, receiver);
            }
            Thread.sleep(messagesTimeout);
        }
        stop();
        return sendTimes;
    }

    private void stop() {
        while (sendTimes.size() != numberOfMessagesToSend) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }
        nodes.forEach(Node::finish);
        executorService.shutdown();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Nodes have not finished their work for 5 seconds, their would be interrupted.");
            executorService.shutdownNow();
        }
        logger.debug("Model was stopped");
    }

    private MacAddress generateAddress(int indexOfNode) {
        byte[] address = new byte[6];
        System.arraycopy(MAC_ADDRESS_START, 0, address, 0, MAC_ADDRESS_START.length);
        address[4] = (byte) ((indexOfNode >> 8) & 0xFF);
        address[5] = (byte) (indexOfNode & 0xFF);
        return new MacAddress(address);
    }
}
