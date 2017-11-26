package ru.sberbank.parallelprog.kolpakov.tokenring;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Node {
    private final static Logger logger = LoggerFactory.getLogger(Node.class.getSimpleName());

    private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
    private final MacAddress address;
    private final Consumer<Frame> frameConsumer;
    private volatile Node nextNode;
    private volatile boolean isFinished;

    public Node(MacAddress address, Consumer<Frame> frameConsumer, Node nextNode) {
        this.address = address;
        this.frameConsumer = frameConsumer;
        this.nextNode = nextNode;
    }

    public MacAddress getAddress() {
        return address;
    }

    public Runnable getQueueTask() {
        return () -> {
            while (!isFinished || !frames.isEmpty()) {
                Frame frame;
                try {
                    while ((frame = frames.poll(100, TimeUnit.MILLISECONDS)) != null) {
                        if (frame.isValid()) {
                            if (address.equals(frame.getDestinationAddress())) {
                                frameConsumer.accept(frame);
                                if(logger.isDebugEnabled()) {
                                    logger.debug("{} was consumed by {}", frame, this);
                                }
                            } else {
                                nextNode.enqueueFrame(frame);
                                if(logger.isTraceEnabled()) {
                                    logger.trace("{} is sent to {} by {}", frame, nextNode, this);
                                }
                            }
                        } else {
                            logger.error("{} received broken {} ", this, frame);
                        }
                    }
                } catch (InterruptedException e) {
                    logger.warn("{} has been interrupted.", this);
                }
            }
        };
    }

    public void finish() {
        isFinished = true;
        if(logger.isDebugEnabled()) {
            logger.debug("flag finished was set for {}", this);
        }
    }

    public void setNextNode(Node nextNode) {
        this.nextNode = nextNode;
    }

    public void enqueueFrame(Frame frame) {
        frames.add(frame);
        if(logger.isTraceEnabled()) {
            logger.trace("{} is added to queue of {}", frame, this);
        }
    }

    @Override
    public String toString() {
        return "Node: " + address;
    }
}
