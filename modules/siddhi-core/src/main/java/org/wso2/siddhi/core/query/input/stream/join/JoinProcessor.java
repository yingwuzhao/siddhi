/*
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.siddhi.core.query.input.stream.join;


import org.wso2.siddhi.core.event.ComplexEvent;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.state.StateEvent;
import org.wso2.siddhi.core.event.state.StateEventPool;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.query.processor.Processor;
import org.wso2.siddhi.core.query.processor.stream.window.FindableProcessor;
import org.wso2.siddhi.core.query.selector.QuerySelector;
import org.wso2.siddhi.core.util.collection.operator.Finder;

import java.util.concurrent.locks.Lock;

/**
 * Created on 12/8/14.
 */
public class JoinProcessor implements Processor {
    private boolean trigger;
    private boolean leftJoinProcessor = false;
    private boolean preJoinProcessor = false;
    private boolean outerJoinProcessor = false;
    private int matchingStreamIndex;
    private Lock joinLock;

    private StateEventPool stateEventPool;
    private Finder finder;
    private FindableProcessor findableProcessor;
    private Processor nextProcessor;
    private QuerySelector selector;

    public JoinProcessor(boolean leftJoinProcessor, boolean preJoinProcessor, boolean outerJoinProcessor, int matchingStreamIndex) {
        this.leftJoinProcessor = leftJoinProcessor;
        this.preJoinProcessor = preJoinProcessor;
        this.outerJoinProcessor = outerJoinProcessor;
        this.matchingStreamIndex = matchingStreamIndex;
    }

    /**
     * Process the handed StreamEvent
     *
     * @param complexEventChunk event chunk to be processed
     */
    @Override
    public void process(ComplexEventChunk complexEventChunk) {
        if (trigger) {
            ComplexEventChunk<StateEvent> returnEventChunk = new ComplexEventChunk<StateEvent>(true);
            StateEvent joinStateEvent = new StateEvent(2, 0);
            StreamEvent nextEvent = (StreamEvent) complexEventChunk.getFirst();
            complexEventChunk.clear();
            while (nextEvent != null) {

                StreamEvent streamEvent = nextEvent;
                nextEvent = streamEvent.getNext();
                streamEvent.setNext(null);
                joinLock.lock();
                try {
                    if (streamEvent.getType() == ComplexEvent.Type.TIMER) {
                        if (preJoinProcessor) {
                            complexEventChunk.add(streamEvent);
                            nextProcessor.process(complexEventChunk);
                            complexEventChunk.clear();
                        }
                        continue;
                    } else if (streamEvent.getType() == ComplexEvent.Type.CURRENT) {
                        if (!preJoinProcessor) {
                            continue;
                        }
                    } else if (streamEvent.getType() == ComplexEvent.Type.EXPIRED) {
                        if (preJoinProcessor) {
                            continue;
                        }
                    } else if (streamEvent.getType() == ComplexEvent.Type.RESET) {
                        continue;
                    }
                    joinStateEvent.setEvent(matchingStreamIndex, streamEvent);
                    StreamEvent foundStreamEvent = findableProcessor.find(joinStateEvent, finder);
                    joinStateEvent.setEvent(matchingStreamIndex, null);
                    if (foundStreamEvent == null) {
                        if (outerJoinProcessor && !leftJoinProcessor) {
                            returnEventChunk.add(joinEventBuilder(foundStreamEvent, streamEvent));
                        } else if (outerJoinProcessor && leftJoinProcessor) {
                            returnEventChunk.add(joinEventBuilder(streamEvent, foundStreamEvent));
                        }
                    } else {
                        while (foundStreamEvent != null) {
                            if (!leftJoinProcessor) {
                                returnEventChunk.add(joinEventBuilder(foundStreamEvent, streamEvent));
                            } else {
                                returnEventChunk.add(joinEventBuilder(streamEvent, foundStreamEvent));
                            }
                            foundStreamEvent = foundStreamEvent.getNext();
                        }
                    }
                    if (preJoinProcessor) {
                        complexEventChunk.add(streamEvent);
                        nextProcessor.process(complexEventChunk);
                        complexEventChunk.clear();
                    }
                } finally {
                    joinLock.unlock();
                }
                if (returnEventChunk.getFirst() != null) {
                    selector.process(returnEventChunk);
                    returnEventChunk.clear();
                }

            }
        } else {
            if (preJoinProcessor) {
                joinLock.lock();
                try {
                    nextProcessor.process(complexEventChunk);
                } finally {
                    joinLock.unlock();
                }
            }
        }
    }

    /**
     * Get next processor element in the processor chain. Processed event should be sent to next processor
     *
     * @return Next Processor
     */
    @Override
    public Processor getNextProcessor() {
        return nextProcessor;
    }

    /**
     * Set next processor element in processor chain
     *
     * @param processor Processor to be set as next element of processor chain
     */
    @Override
    public void setNextProcessor(Processor processor) {
        nextProcessor = processor;
    }

    public void setJoinLock(Lock joinLock) {
        this.joinLock = joinLock;
    }

    /**
     * Set as the last element of the processor chain
     *
     * @param processor Last processor in the chain
     */
    @Override
    public void setToLast(Processor processor) {
        if (nextProcessor == null) {
            this.nextProcessor = processor;
        } else {
            this.nextProcessor.setToLast(processor);
        }
        if (processor instanceof QuerySelector) {
            selector = (QuerySelector) processor;
        }
    }

    /**
     * Clone a copy of processor
     *
     * @param key partition key
     * @return Cloned Processor
     */
    @Override
    public Processor cloneProcessor(String key) {
        JoinProcessor joinProcessor = new JoinProcessor(leftJoinProcessor, preJoinProcessor, outerJoinProcessor, matchingStreamIndex);
        joinProcessor.setTrigger(trigger);
        if (trigger) {
            joinProcessor.setFinder(finder.cloneFinder(key));
        }
        return joinProcessor;
    }

    public void setFindableProcessor(FindableProcessor findableProcessor) {
        this.findableProcessor = findableProcessor;
    }

    public void setFinder(Finder finder) {
        this.finder = finder;
    }

    public void setTrigger(boolean trigger) {
        this.trigger = trigger;
    }

    public void setStateEventPool(StateEventPool stateEventPool) {
        this.stateEventPool = stateEventPool;
    }

    /**
     * Join the given two event streams
     *
     * @param leftStream  event left stream
     * @param rightStream event right stream
     * @return StateEvent state event
     */
    public StateEvent joinEventBuilder(StreamEvent leftStream, StreamEvent rightStream) {
        StateEvent returnEvent = stateEventPool.borrowEvent();
        returnEvent.setEvent(0, leftStream);
        returnEvent.setEvent(1, rightStream);
        if (preJoinProcessor) {
            returnEvent.setType(ComplexEvent.Type.CURRENT);
        } else {
            returnEvent.setType(ComplexEvent.Type.EXPIRED);
        }
        if (!leftJoinProcessor) {
            returnEvent.setTimestamp(rightStream.getTimestamp());
        } else {
            returnEvent.setTimestamp(leftStream.getTimestamp());
        }
        return returnEvent;
    }
}
