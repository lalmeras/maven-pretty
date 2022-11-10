package likide.pretty;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;

import javax.inject.Named;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionEvent.Type;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.LoggerManager;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import likide.pretty.model.ProjectStatus;
import likide.pretty.model.ProjectStep;
import likide.pretty.model.Status;

@Named("maven-pretty")
@Component(role = EventSpy.class, hint = "output", description = "Pretty output for maven build.")
public class PrettyEventSpy extends AbstractEventSpy {

	@Requirement
	private LoggerManager loggerManager;

	private AtomicReference<Thread> outputThread = new AtomicReference<>();
	private AtomicBoolean terminated = new AtomicBoolean(false);
	private Map<MavenProject, Deque<ProjectStatus>> queues = new ConcurrentHashMap<>();
	private Map<MavenProject, ProjectStatus> lastStatuses = new ConcurrentHashMap<>();
	private int lastLoopLength = 0;
	private PrintStream output = System.out;
	private File mavenOutputFile = null;
	private Terminal terminal;

	@Override
	public synchronized void onEvent(Object event) throws Exception {
		try {
			if (outputThread.get() == null && Boolean.toString(true).equals(System.getenv().getOrDefault("PRETTY", "false"))) {
				terminal = TerminalBuilder.terminal();
				outputThread.set(new Thread(this::output));
				outputThread.get().setDaemon(true);
				outputThread.get().start();
			}
			if (event instanceof DefaultMavenExecutionRequest) {
				((DefaultMavenExecutionRequest) event).setTransferListener(new PrettyTransferListener());
			}
			if (event instanceof ExecutionEvent) {
				ExecutionEvent executionEvent = (ExecutionEvent) event;
				if (Type.SessionStarted.equals(executionEvent.getType())) {
					for (MavenProject project : executionEvent.getSession().getProjects()) {
						queues.computeIfAbsent(project, (i) -> new ArrayDeque<>(10)).offer(planned(project));
					}
				}
				MavenProject project = executionEvent.getProject();
				if (project != null) {
					if (Type.ProjectSucceeded.equals(executionEvent.getType())) {
						queues.computeIfAbsent(project, (i) -> new ArrayDeque<>(10)).offer(built(project));
					} else if (Type.ProjectFailed.equals(executionEvent.getType())) {
						queues.computeIfAbsent(project, (i) -> new ArrayDeque<>(10)).offer(failed(project));
					} else if (Type.ProjectSkipped.equals(executionEvent.getType())) {
						queues.computeIfAbsent(project, (i) -> new ArrayDeque<>(10)).offer(skipped(project));
					} else if (executionEvent.getMojoExecution() != null) {
						queues.computeIfAbsent(project, (i) -> new ArrayDeque<>(10)).offer(execution(project, executionEvent.getMojoExecution(), executionEvent.getType()));
					} else {
						queues.computeIfAbsent(project, (i) -> new ArrayDeque<>(10)).offer(started(project));
					}
				}
				if (Type.SessionStarted.equals(executionEvent.getType())) {
					mavenOutputFile = File.createTempFile("maven-", ".log");
					PrintStream filePrintStream = new PrintStream(mavenOutputFile);
					System.setOut(filePrintStream);
					System.setErr(filePrintStream);
				}
				if (Type.SessionEnded.equals(executionEvent.getType())) {
					// wait for output termination
					terminated.set(true);
					outputThread.get().join();
				}
			}
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
	}

	private ProjectStatus started(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, Status.PLANNED, null, null);
	}

	private ProjectStatus planned(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, Status.PLANNED, null, null);
	}

	private ProjectStatus execution(MavenProject mavenProject, MojoExecution mojoExecution, Type type) {
		return new ProjectStatus(mavenProject, Status.BUILDING, new ProjectStep(mojoExecution, type), null);
	}

	private ProjectStatus built(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, Status.SUCCESS, null, null);
	}

	private ProjectStatus failed(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, Status.FAILED, null, null);
	}

	private ProjectStatus skipped(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, Status.SKIPPED, null, null);
	}

	public void output() {
		try {
			int clock = 0;
			boolean empty = false;
			while (!terminated.get() || !empty) {
				clock++;
				clock = clock%10000;
				empty = printFrame(clock);
				Thread.sleep(TimeUnit.MILLISECONDS.toMillis(50));
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		output.println(String.format("Maven output available in %s", mavenOutputFile));
	}

	private boolean printFrame(int clock) {
		// TODO: limit display when there is more projects than terminal line count
		// Print one line with skipped projects (N projects skipped)
		boolean empty = true;
		List<String> building = new ArrayList<>();
		List<String> finished = new ArrayList<>();
		int nbFailed = 0;
		int nbSuccess = 0;
		int nbPlanned = 0;
		int nbSkipped = 0;
		int width = terminal.getWidth();
		for (Entry<MavenProject, Deque<ProjectStatus>> entry : queues.entrySet()) {
			StringBuilder sb = new StringBuilder();
			ProjectStatus lastStatus = lastStatuses.get(entry.getKey());
			ProjectStatus currentStatus = entry.getValue().poll();
			ProjectStatus effectiveStatus;
			if (currentStatus != null) {
				empty = false;
				effectiveStatus = new ProjectStatus(currentStatus, lastStatus);
				lastStatuses.put(entry.getKey(), effectiveStatus);
			} else {
				effectiveStatus = lastStatus;
			}
			sb.append(effectiveStatus.toString(clock));
			if (effectiveStatus.getStatus().isFinished()) {
				if (currentStatus != null) {
					finished.add(sb.toString());
				}
				if (Status.SUCCESS.equals(effectiveStatus.getStatus())) {
					nbSuccess++;
				} else if (Status.SKIPPED.equals(effectiveStatus.getStatus())) {
					nbSkipped++;
				} else if (Status.FAILED.equals(effectiveStatus.getStatus())) {
					nbFailed++;
				}
			} else if (Status.PLANNED.equals(effectiveStatus.getStatus())) {
				nbPlanned++;
			} else {
				building.add(sb.toString());
			}
		}
		for (int i = 0; i < lastLoopLength; i++) {
			output.print(String.format(Constants.TERM_ESCAPE + Constants.TERM_LINE_UP, 1));
			output.print(Constants.TERM_ESCAPE + Constants.TERM_LINE_BACK);
		}
		for (String finishedItem : finished) {
			printLine(output, finishedItem, width);
		}
		int loopLength = 0;
		for (String buildingItem : building) {
			printLine(output, buildingItem, width);
			loopLength++;
		}
		printLine(output, String.format("Built " + Constants.TERM_ESCAPE + Constants.TERM_BOLD + "%5$d/%4$d" + Constants.TERM_RESET + " projects... Failed: %1$d - Success: %2$d - Planned: %3$d - Skipped: %4$s",
				nbFailed, nbSuccess, nbPlanned, queues.size(), nbSuccess, nbSkipped), width);
		loopLength++;
		lastLoopLength = loopLength;
		return empty;
	}

	private void printLine(PrintStream out, String value, int maxWidth) {
		out.println(ellipsize(value, maxWidth, ">", 1));
	}

	public static String ellipsize(String value, int maxWidth) {
		return ellipsize(value, maxWidth, "", 0);
	}

	public static String ellipsize(String value, int maxWidth, String suffix, int suffixLength) {
		boolean resetNeeded = false;
		boolean suffixNeeded = false;
		int realLength = 0;
		int lastStop = 0;
		StringBuilder ellipsized = new StringBuilder();
		Matcher m = Constants.ESCAPE_PATTERN.matcher(value);
		MatchResult[] matches = m.results().toArray(i -> new MatchResult[i]);
		if (matches.length == 0) {
			if (value.length() <= maxWidth) {
				return value;
			} else if (value.length() == maxWidth) {
				return value.substring(0, maxWidth);
			} else {
				return value.substring(0, maxWidth - suffixLength) + suffix;
			}
		}
		for (MatchResult g : matches) {
			// length: increment from last regexp match to current regexp start
			realLength += g.start() - lastStop;
			// check if adding last segment overflow maxWidth
			int stop = g.start();
			if (realLength >= maxWidth) {
				// update stop to truncate segment at appropriate size
				stop -= realLength - maxWidth;
				if (length(value.substring(stop)) > 0) {
					suffixNeeded = true;
					stop -= suffixLength;
				}
			}
			ellipsized.append(value.substring(lastStop, stop));
			if (realLength >= maxWidth) {
				break;
			}
			ellipsized.append(value.substring(g.start(), g.end()));
			lastStop = g.end();
			resetNeeded = !resetNeeded;
		}
		if (resetNeeded) {
			ellipsized.append(Constants.TERM_RESET);
		}
		if (suffixNeeded) {
			ellipsized.append(suffix);
		}
		return ellipsized.toString();
	}

	private static int length(String value) {
		return Constants.ESCAPE_PATTERN.matcher(value).replaceAll("").length();
	}
}
