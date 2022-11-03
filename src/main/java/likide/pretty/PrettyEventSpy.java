package likide.pretty;

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
import java.util.stream.Collectors;

import javax.inject.Named;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionEvent.Type;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

@Named("maven-pretty")
@Component(role = EventSpy.class, hint = "output", description = "Pretty output for maven build.")
public class PrettyEventSpy extends AbstractEventSpy {
	private static final String TERM_LINE_UP = "%dA";
	private static final String TERM_LINE_BACK = "2K";
	private static final String TERM_ESCAPE = "\033[";
	private static final String TERM_RESET = "\033[0m";
	private static final String TERM_BOLD_GREEN = "1;32m";
	private static final String TERM_BOLD_YELLOW = "1;33m";
	private static final String TERM_BOLD_RED = "1;31m";
	private static final String TERM_BOLD_BLUE = "1;34m";

	private AtomicReference<Thread> outputThread = new AtomicReference<>();
	private AtomicBoolean terminated = new AtomicBoolean(false);
	private Map<MavenProject, Deque<ProjectStatus>> output = new ConcurrentHashMap<>();
	private Map<MavenProject, ProjectStatus> lastStatuses = new ConcurrentHashMap<>();
	private int lastLoopLength = 0;

	@Override
	public synchronized void onEvent(Object event) throws Exception {
		try {
			if (outputThread.get() == null && Boolean.toString(true).equals(System.getenv().getOrDefault("PRETTY", "false"))) {
				outputThread.set(new Thread(this::output));
				outputThread.get().setDaemon(true);
				outputThread.get().start();
			}
			if (event instanceof ExecutionEvent) {
				ExecutionEvent executionEvent = (ExecutionEvent) event;
				if (Type.SessionStarted.equals(executionEvent.getType())) {
					for (MavenProject project : executionEvent.getSession().getProjects()) {
						output.computeIfAbsent(project, (i) -> new ArrayDeque<>(10)).offer(planned(project));
					}
				}
				MavenProject project = executionEvent.getProject();
				if (project != null) {
					if (Type.ProjectSucceeded.equals(executionEvent.getType())) {
						output.computeIfAbsent(project, (i) -> new ArrayDeque<>(10)).offer(built(project));
					} else if (Type.ProjectFailed.equals(executionEvent.getType())) {
						output.computeIfAbsent(project, (i) -> new ArrayDeque<>(10)).offer(failed(project));
					} else if (executionEvent.getMojoExecution() != null) {
						output.computeIfAbsent(project, (i) -> new ArrayDeque<>(10)).offer(execution(project, executionEvent.getMojoExecution(), executionEvent.getType()));
					} else {
						output.computeIfAbsent(project, (i) -> new ArrayDeque<>(10)).offer(started(project));
					}
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

	enum ProjectStatusType {
		PLANNED,
		BUILDING,
		SUCCESS,
		FAILED;
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
			return String.format("%s %s:%s:%s: %s", status(clock), mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion(), lastSteps());
		}
		
		public String status(int clock) {
			switch (status) {
			case BUILDING:
				switch ((clock/10)%3) {
					case 0:
						return TERM_ESCAPE + TERM_BOLD_BLUE + "․" + TERM_RESET;
					case 1:
						return TERM_ESCAPE + TERM_BOLD_BLUE + "‥" + TERM_RESET;
					case 2:
						return TERM_ESCAPE + TERM_BOLD_BLUE + "…" + TERM_RESET;
					default:
						throw new IllegalStateException();
				}
			case FAILED:
				return TERM_ESCAPE + TERM_BOLD_RED + "✘" + TERM_RESET;
			case PLANNED:
				return TERM_ESCAPE + TERM_BOLD_YELLOW + "⧖" + TERM_RESET;
			case SUCCESS:
				return TERM_ESCAPE + TERM_BOLD_GREEN + "✔" + TERM_RESET;
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
	}

	public class ProjectMojoExecution {
		private final String key;
		private final String executionId;
		private final Type status;
		private final String goal;
		
		public ProjectMojoExecution(MojoExecution execution, Type status) {
			this.key = execution.toString();
			this.executionId = execution.getExecutionId();
			this.status = status;
			this.goal = execution.getGoal();
		}
	}

	public void output() {
		try {
			// TODO: limit display when there is more projects than terminal line count
			// Isolate waiting / building / finished projects
			// Print and do not erase finished projects
			// Print one line with skipped projects (N projects skipped)
			// Print one line with built projects (N projects built successfully)
			// Print one line with failed projects (N projects failed)
			// Print one line with waiting projects (N projects waiting to be processed)
			// Print lines with built projects
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
	}

	private boolean printFrame(int clock) {
		StringBuilder sb = new StringBuilder();
		int loopLength = 0;
		boolean empty = true;
		for (Entry<MavenProject, Deque<ProjectStatus>> entry : output.entrySet()) {
			ProjectStatus lastStatus = lastStatuses.get(entry.getKey());
			ProjectStatus currentStatus = entry.getValue().poll();
			if (currentStatus != null) {
				empty = false;
				currentStatus = new ProjectStatus(currentStatus, lastStatus);
				lastStatuses.put(entry.getKey(), currentStatus);
			} else {
				currentStatus = lastStatus;
			}
			sb.append(currentStatus.toString(clock));
			sb.append("\n");
			loopLength++;
		}
		for (int i = 0; i < lastLoopLength; i++) {
			System.out.print(TERM_ESCAPE + TERM_LINE_BACK);
			System.out.print(String.format(TERM_ESCAPE + TERM_LINE_UP, 1));
			System.out.print(TERM_ESCAPE + TERM_LINE_BACK);
		}
		lastLoopLength = loopLength;
		System.out.print(sb);
		return empty;
	}
}
