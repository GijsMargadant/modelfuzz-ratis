/*******************************************************************************
 * Copyright (c) 2018 Microsoft Research. All rights reserved. 
 *
 * The MIT License (MIT)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy 
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software. 
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Contributors:
 *   William Schultz - initial API and implementation
 ******************************************************************************/
package tlc2.controlled;

import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import tlc2.TLCGlobals;
import tlc2.controlled.protocol.ActionMapper;
import tlc2.controlled.protocol.ActionMapperFactory;
import tlc2.controlled.protocol.ActionWrapper;
import tlc2.tool.*;
import tlc2.tool.liveness.ILiveCheck;
import tlc2.util.RandomGenerator;
import util.FileUtil;

/**
 * ControlledWorker is a SimulationWorker with controlled inputs for the next step
 * 
 * It is constructed with an initial seed which it uses to initialize its
 * internal random number generator.
 * A worker communicates the errors it encounters
 * by pushing its results onto a result queue, that is provided to it at construction time.
 *
 * 
 * Upon termination due to any cause, a worker thread will always push a final
 * OK result onto its result queue. This acts as a way to signal external
 * clients that this thread has terminated.
 */
public class ControlledWorker extends SimulationWorker {

	protected static final boolean coverage = TLCGlobals.isCoverageEnabled();

	protected Map<String, String> mapperParams;

	public ControlledWorker(int id, ITool tool, BlockingQueue<SimulationWorker.SimulationWorkerResult> resultQueue,
								long seed, int maxTraceDepth, long maxTraceNum, boolean checkDeadlock, String traceFile,
								ILiveCheck liveCheck, Map<String, String> mapperParams) {
		super(id, tool, resultQueue, seed, maxTraceDepth, maxTraceNum, null, checkDeadlock, traceFile, liveCheck,
				new LongAdder(), new AtomicLong(), new AtomicLong());
				this.mapperParams = mapperParams;
	}

	public ControlledWorker(int id, ITool tool, BlockingQueue<SimulationWorker.SimulationWorkerResult> resultQueue,
			long seed, int maxTraceDepth, long maxTraceNum, String traceActions, boolean checkDeadlock, String traceFile,
			ILiveCheck liveCheck, LongAdder numOfGenStates, AtomicLong numOfGenTraces, AtomicLong m2AndMean, Map<String, String> mapperParams) {
		super(id, tool, resultQueue, seed, maxTraceDepth, maxTraceNum, traceActions, checkDeadlock,
				traceFile, liveCheck, numOfGenStates, numOfGenTraces, m2AndMean);
		this.mapperParams = mapperParams;
	}

	protected boolean simulateAndReport() {
		try {
			// The trace simulation method should do appropriately frequent interruption
			// checks.
			globalTraceCnt = this.numOfGenTraces.incrementAndGet();
			final Optional<SimulationWorker.SimulationWorkerError> res = simulateRandomTrace();
			traceCnt++;

			// If we have an error result, place it on the output queue.
			if (res.isPresent()) {
				final SimulationWorker.SimulationWorkerError err = res.get();
				resultQueue.put(SimulationWorker.SimulationWorkerResult.Error(this.myGetId(), err));
				// One would assume to return from this branch to stop the worker from creating
				// additional behaviors. However, this is at the discretion of Simulator, which
				// checks if the user ran simulation with "-continue".  If not, Simulator
				// will signal termination asynchronously.
			}

			// Abide by the maximum trace generation count.
			if (traceCnt >= maxTraceNum) {
				resultQueue.put(SimulationWorker.SimulationWorkerResult.OK(this.myGetId()));
				return false;
			}
			return true;
		} catch (final InterruptedException e) {
			// Gracefully terminate if we were interrupted.
			resultQueue.offer(SimulationWorker.SimulationWorkerResult.OK(this.myGetId()));
			return false;
		} catch (final Exception e) {
			final SimulationWorker.SimulationWorkerError err = new SimulationWorker.SimulationWorkerError(0, null, this.curState, this.getTrace(), e);
			resultQueue.offer(SimulationWorker.SimulationWorkerResult.Error(this.myGetId(), err));
			return false;
		}	
	}

	/**
	 * This method returns a state that is randomly chosen from the set of states.
	 * It returns null if the set of states is empty.
	 */
	protected TLCState randomState(RandomGenerator rng, StateVec states) {
		final int len = states.size();
		if (len > 0) {
			final int index = (int) Math.floor(rng.nextDouble() * len);
			return states.elementAt(index);
		}
		return null;
	}
	

	protected int getNextActionIndex(RandomGenerator rng, Action[] actions, TLCState curState) {
		return (int) Math.floor(this.localRng.nextDouble() * actions.length);
	}

	protected int getNextActionIndex(int index, Action[] actions) {
		return (int) Math.floor(index * actions.length);
	}

	/**
	 * Generates a single trace controlled by an ActionController.
	 *
	 * The core steps of this process are as follows:
	 * 
	 *  a) Pick one of the initial states.
	 *  	// ControlledWorker currently assumes a single initial state (and checks with an assertion)
	 *  b) Run the controller-selected action to generate the successor states (more than 1) to the current initial state.
	 *  	// ControlledWorker currently assumes a single successor state for each event (and checks with an assertion)
	 *  c) Check the generated successor for their validity.
	 *  d) Make the successor state the new current state.
	 *
	 * Returns an Optional error representing the outcome of the trace generation. If the trace generation produced no error,
	 * returns Optional.empty().
	 *
	 */
	protected Optional<SimulationWorker.SimulationWorkerError> simulateRandomTrace() throws Exception {

		// a) Randomly select a state from the set of init states.
		assert(initStates.size() == 1);
		curState = randomState(this.localRng, initStates);
		setCurrentState(curState);

		System.out.println("[Worker] Initial state: " + curState);

		ActionMapper mapper = ActionMapperFactory.getMapper(this.mapperParams, Arrays.asList(this.tool.getActions()), this.tool.getRootName());
		// new RemoteController(mapper, this.tool.getActions());
		ActionController controller = new CmdLineController(mapper, this.tool.getActions(), curState);

		// Actions to run asked by the controller
		Queue<ActionWrapper> actionsToRun = new ArrayDeque<>();
		// States visited in correspondence, to send to the controller
		List<TLCState> statesVisited = new ArrayList<>();
		statesVisited.add(curState);
		boolean quit = false;

		// Simulate a trace up to the maximum specified length.
		for (int traceIdx = 0; traceIdx < maxTraceDepth && !quit; traceIdx++) {
			// We don't want this thread to run for too long without checking for
			// interruption, so we do so on every iteration of the main trace generation
			// loop.
			// checkForInterrupt();

			// b) Get the current state's successor states.
			nextStates.clear();
			while(nextStates.empty()) {
				try {

					if(actionsToRun.isEmpty()) {
						controller.setVisitedStates(statesVisited); // initially it adds the initial state
						statesVisited.clear();
						actionsToRun.addAll(controller.getNextActions());
					}

					ActionWrapper nextActionWrapper = actionsToRun.remove();
					if(nextActionWrapper.isQuit() || nextActionWrapper.isReset()) {
						quit = true;
						break;
					}
					Action nextAction = nextActionWrapper.action;
					if(nextAction.equals(Action.UNKNOWN)) { // next action is always non-null (or remove throws an exception)
						quit = true;
						break;
					} else {
						this.tool.getNextStates(this, curState, nextAction); // fills in nextStates

						if(nextStates.empty()) {
							statesVisited.add(curState);
						}
					}
				} catch (SimulationWorkerError swe) {
					// getNextState doesn't throw SWE unless SimulationWorker#addElement above throws it.
					return Optional.of(swe);
				}
			}

			// We currently assume there is a single next state
			assert (nextStates.size() == 1);

			// d) Set the current state for the next iteration of the loop.
			// final TLCState s1 = randomState(localRng, nextStates);
			final TLCState s1 = nextStates.elementAt(0);

			// Execute callable on the state that was selected from the set of successor
			// states.  See TLCExt!TLCDefer operator for context.
			s1.execCallable();

			// In case actionStats are off, we waste a few cycles to increment this counter
			// nobody is going to look at.
			if (traceActions != null) {
				this.actionStats[curState.getAction().getId()][s1.getAction().getId()]++;
			}
			curState = s1;
			setCurrentState(curState);
			statesVisited.add(curState);
		}

		// Check for interruption once more before entering liveness checking.
		// checkForInterrupt();

		// Check if the current trace satisfies liveness properties.
		liveCheck.checkTrace(tool.getLiveness(), new Supplier<StateVec>() {
			@Override
			public StateVec get() {
				// Pass a supplier instead of the trace directly to convert
				// the linked list TLCStateMutExt <- TLCStateMutExt to an array
				// iff liveness checking is enabled.
				return getTrace();
			}
		});
		
		// Take the minimum of maxTraceDepth and getLevel here because - historically -
		// the for loop above would add the chosen next-state from loop N in loop N+1.
		// Thus, the final state that is generated before traceCnt = maxTraceDepth,
		// wasn't getting added to the stateVec (check git history) whose length was
		// passed to welfordM2AndMean.
		welfordM2AndMean.accumulateAndGet(Math.min(maxTraceDepth, curState.getLevel()), (acc, tl) -> {
			// Welford's online algorithm (m2 and mean stuffed into high and low of the
			// atomiclong because welfordM2AndMean is updated concurrently by multiple workers).
			// https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Welford's_online_algorithm
			int mean = (int) (acc & 0x00000000FFFFFFFFL);
			long m2 = acc >>> 32;
			final long delta = tl - mean;
			mean += delta / (numOfGenTraces.longValue() + 0); //+1 prevent div-by-zero
			m2 += delta * (tl - mean);
			return m2 << 32 | (mean & 0xFFFFFFFFL);
		});
		
		// Write the trace out if desired. The trace is printed in the
		// format of TLA module, so that it can be read by TLC again.
		if (traceFile != null) {
			// Make sure each worker outputs to its own set of trace files.
			final String fileName = traceFile + "_" + String.valueOf(this.myGetId()) + "_" + this.traceCnt;
			// TODO is it ok here?
			final PrintWriter pw = new PrintWriter(FileUtil.newBFOS(fileName));
			pw.println("---------------- MODULE " + fileName + " -----------------");
			final StateVec stateTrace = new Supplier<StateVec>() {
				@Override
				public StateVec get() {
					return getTrace();
				}
			}.get();
			for (int idx = 0; idx < stateTrace .size(); idx++) {
				Action curAction = stateTrace.elementAt(idx).getAction();
				if (curAction != null) {
					pw.println("\\* " + curAction.getLocation());
				}
				pw.println("STATE_" + (idx + 1) + " == ");
				pw.println(stateTrace.elementAt(idx) + "\n");
			}
			pw.println("=================================================");
			pw.close();
		}

		postTrace(curState);
		
		// Finished trace generation without any errors.
		return Optional.empty();
	}
}