package likide.pretty;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

@Named("maven-pretty")
@Component(role = EventSpy.class, hint = "output", description = "Pretty output for maven build.")
public class PrettyEventSpy extends AbstractEventSpy {
	private static final Pattern ESCAPE_PATTERN = Pattern.compile("\033\\[[^m]*m");
	private static final String TERM_LINE_UP = "%dA";
	private static final String TERM_LINE_BACK = "2K";
	private static final String TERM_ESCAPE = "\033[";
	private static final String TERM_RESET = "\033[0m";
	private static final String TERM_BOLD = "1m";
	private static final String TERM_BOLD_GREEN = "1;32m";
	private static final String TERM_BOLD_YELLOW = "1;33m";
	private static final String TERM_BOLD_RED = "1;31m";
	private static final String TERM_BOLD_BLUE = "1;34m";
	private static final String TERM_GREY = "38;5;8m";

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
				((DefaultMavenExecutionRequest) event).setTransferListener(new TransferListener() {
					
					@Override
					public void transferSucceeded(TransferEvent event) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void transferStarted(TransferEvent event) throws TransferCancelledException {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void transferProgressed(TransferEvent event) throws TransferCancelledException {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void transferInitiated(TransferEvent event) throws TransferCancelledException {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void transferFailed(TransferEvent event) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void transferCorrupted(TransferEvent event) throws TransferCancelledException {
						// TODO Auto-generated method stub
						
					}
				});
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
		return new ProjectStatus(mavenProject, ProjectStatusType.PLANNED, null, null);
	}

	private ProjectStatus planned(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, ProjectStatusType.PLANNED, null, null);
	}

	private ProjectStatus execution(MavenProject mavenProject, MojoExecution mojoExecution, Type type) {
		return new ProjectStatus(mavenProject, ProjectStatusType.BUILDING, new ProjectMojoExecution(mojoExecution, type), null);
	}

	private ProjectStatus built(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, ProjectStatusType.SUCCESS, null, null);
	}

	private ProjectStatus failed(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, ProjectStatusType.FAILED, null, null);
	}

	private ProjectStatus skipped(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, ProjectStatusType.SKIPPED, null, null);
	}

	enum ProjectStatusType {
		PLANNED,
		BUILDING,
		SUCCESS,
		FAILED,
		SKIPPED;
		public boolean isFinished() {
			return this == SUCCESS || this == FAILED;
		}
	}

	public class ProjectStatus {
		private final MavenProject mavenProject;
		private final ProjectStatusType status;
		private final List<ProjectMojoExecution> previousSteps;
		private final ProjectMojoExecution currentStep;
		
		public ProjectStatus(ProjectStatus currentStatus, ProjectStatus lastStatus) {
			this(currentStatus.mavenProject, currentStatus.status, currentStatus.currentStep, lastStatus);
		}
		
		public ProjectStatus(MavenProject mavenProject, ProjectStatusType status, ProjectMojoExecution currentStep, ProjectStatus lastStatus) {
			this.mavenProject = mavenProject;
			this.status = status;
			this.currentStep = currentStep;
			if (lastStatus != null) {
				List<ProjectMojoExecution> steps = new ArrayList<>();
				if(lastStatus.previousSteps != null && !lastStatus.previousSteps.isEmpty()) {
					steps.addAll(lastStatus.previousSteps);
				}
				if (lastStatus.currentStep != null && !Type.MojoStarted.equals(lastStatus.currentStep.status)) {
					steps.add(lastStatus.currentStep);
				}
				previousSteps = List.copyOf(steps);
			} else {
				previousSteps = null;
			}
		}
		
		public String toString(int clock) {
			return String.format("%s %s: %s", status(clock), mavenProject.getArtifactId(), lastPhases(clock));
		}
		
		public String status(int clock) {
			switch (status) {
			case BUILDING:
				return dot(clock);
			case FAILED:
				return TERM_ESCAPE + TERM_BOLD_RED + "✘" + TERM_RESET;
			case PLANNED:
				return TERM_ESCAPE + TERM_BOLD_BLUE + "⧖" + TERM_RESET;
			case SUCCESS:
				return TERM_ESCAPE + TERM_BOLD_GREEN + "✔" + TERM_RESET;
			default:
				throw new IllegalStateException();
			}
		}
		
		public String dot(int clock) {
			switch ((clock/10)%3) {
			case 0:
				return TERM_ESCAPE + TERM_BOLD_YELLOW + "․" + TERM_RESET;
			case 1:
				return TERM_ESCAPE + TERM_BOLD_YELLOW + "‥" + TERM_RESET;
			case 2:
				return TERM_ESCAPE + TERM_BOLD_YELLOW + "…" + TERM_RESET;
			default:
				throw new IllegalStateException();
			}
		}
		
		public String lastSteps() {
			if (previousSteps == null) {
				return "";
			}
			return previousSteps.stream().map(i -> i.goal).collect(Collectors.joining(", ", "(", ")")) + " ";
		}
		
		public String lastPhases(int clock) {
			if (previousSteps == null) {
				return "";
			}
			List<String> phases = previousSteps.stream().map(i -> phaseOrPlugin(i)).distinct().collect(Collectors.toList());
			if (ProjectStatusType.SUCCESS.equals(status)) {
				if (phases.isEmpty()) {
					return "";
				} else if (phases.size() == 1) {
					return TERM_ESCAPE + TERM_GREY + phases.get(0) + TERM_RESET;
				} else {
					return String.format(TERM_ESCAPE + TERM_GREY + "%s … %s", phases.get(0), phases.get(phases.size() - 1) + TERM_RESET);
				}
			}
			StringBuilder sb = new StringBuilder();
			sb.append(phases.stream().collect(Collectors.joining(", ")));
			if (currentStep != null && !phases.isEmpty()) {
				String lastBeforeCurrent = phases.stream().skip(phases.size() - 1l).findFirst().orElse(null);
				if (lastBeforeCurrent != null && lastBeforeCurrent.equals(currentStep.phase)) {
					// last phase is still performing
					sb.append(dot(clock));
				} else if (lastBeforeCurrent != null && !lastBeforeCurrent.equals(currentStep.phase)) {
					sb.append(", " + currentStep.phase + dot(clock));
				} else if (lastBeforeCurrent == null) {
					sb.append(currentStep.phase + dot(clock));
				}
			} else if (currentStep != null) {
				sb.append(currentStep.phase + dot(clock));
			}
			return sb.toString();
		}
		
		public String phaseOrPlugin(ProjectMojoExecution execution) {
			return Optional.ofNullable(execution.phase).orElseGet(() -> shortGoal(execution));
		}
		
		public String shortGoal(ProjectMojoExecution execution) {
			if (execution.artifactId.startsWith("maven-") && execution.artifactId.endsWith("-plugin")) {
				return String.format("%s:%s", execution.artifactId.substring("maven-".length(), execution.artifactId.length() - "-plugin".length()), execution.goal);
			} else {
				return String.format("%s:%s", execution.artifactId, execution.goal);
			}
		}
	}

	public class ProjectMojoExecution {
		private final String key;
		private final String executionId;
		private final Type status;
		private final String goal;
		private final String phase;
		private final String groupId;
		private final String artifactId;
		
		public ProjectMojoExecution(MojoExecution execution, Type status) {
			this.key = execution.toString();
			this.executionId = execution.getExecutionId();
			this.status = status;
			this.goal = execution.getGoal();
			this.phase = execution.getLifecyclePhase();
			this.groupId = execution.getGroupId();
			this.artifactId = execution.getArtifactId();
		}
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
			if (effectiveStatus.status.isFinished()) {
				if (currentStatus != null) {
					finished.add(sb.toString());
				}
				if (ProjectStatusType.SUCCESS.equals(effectiveStatus.status)) {
					nbSuccess++;
				} else if (ProjectStatusType.SKIPPED.equals(effectiveStatus.status)) {
					nbSkipped++;
				} else if (ProjectStatusType.FAILED.equals(effectiveStatus.status)) {
					nbFailed++;
				}
			} else if (ProjectStatusType.PLANNED.equals(effectiveStatus.status)) {
				nbPlanned++;
			} else {
				building.add(sb.toString());
			}
		}
		for (int i = 0; i < lastLoopLength; i++) {
			output.print(String.format(TERM_ESCAPE + TERM_LINE_UP, 1));
			output.print(TERM_ESCAPE + TERM_LINE_BACK);
		}
		for (String finishedItem : finished) {
			printLine(output, finishedItem, width);
		}
		int loopLength = 0;
		for (String buildingItem : building) {
			printLine(output, buildingItem, width);
			loopLength++;
		}
		printLine(output, String.format("Built " + TERM_ESCAPE + TERM_BOLD + "%5$d/%4$d" + TERM_RESET + " projects... Failed: %1$d - Success: %2$d - Planned: %3$d - Skipped: %4$s",
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
		Matcher m = ESCAPE_PATTERN.matcher(value);
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
			ellipsized.append(TERM_RESET);
		}
		if (suffixNeeded) {
			ellipsized.append(suffix);
		}
		return ellipsized.toString();
	}

	private static int length(String value) {
		return ESCAPE_PATTERN.matcher(value).replaceAll("").length();
	}
}
