package likide.pretty;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

	private AtomicReference<Thread> outputThread = new AtomicReference<>();
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
				Thread.sleep(TimeUnit.MILLISECONDS.toMillis(100));
			}
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
	}

	private ProjectStatus started(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, "started", null, null);
	}

	private ProjectStatus planned(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, "planned", null, null);
	}

	private ProjectStatus execution(MavenProject mavenProject, MojoExecution mojoExecution, Type type) {
		return new ProjectStatus(mavenProject, mojoExecution.toString(), new ProjectMojoExecution(mojoExecution, type), null);
	}

	private ProjectStatus built(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, "built", null, null);
	}

	private ProjectStatus failed(MavenProject mavenProject) {
		return new ProjectStatus(mavenProject, "failed", null, null);
	}

	public class ProjectStatus {
		private final MavenProject mavenProject;
		private final String status;
		private final List<ProjectMojoExecution> previousSteps;
		private final ProjectMojoExecution currentStep;
		
		public ProjectStatus(ProjectStatus currentStatus, ProjectStatus lastStatus) {
			this(currentStatus.mavenProject, currentStatus.status, currentStatus.currentStep, lastStatus);
		}
		
		public ProjectStatus(MavenProject mavenProject, String status, ProjectMojoExecution currentStep, ProjectStatus lastStatus) {
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
		
		@Override
		public String toString() {
			return String.format("%s:%s:%s: %s%s", mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion(), lastSteps(), status);
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
			while (true) {
				StringBuilder sb = new StringBuilder();
				int loopLength = 0;
				for (Entry<MavenProject, Deque<ProjectStatus>> entry : output.entrySet()) {
					ProjectStatus lastStatus = lastStatuses.get(entry.getKey());
					ProjectStatus currentStatus = entry.getValue().poll();
					if (currentStatus != null) {
						currentStatus = new ProjectStatus(currentStatus, lastStatus);
						lastStatuses.put(entry.getKey(), currentStatus);
					} else {
						currentStatus = lastStatus;
					}
					sb.append(currentStatus);
					sb.append("\n");
					loopLength++;
				}
				for (int i = 0; i < lastLoopLength; i++) {
					System.out.print("\033[2K");
					System.out.print(String.format("\033[%dA", 1));
					System.out.print("\033[2K");
				}
				lastLoopLength = loopLength;
				System.out.print(sb);
				Thread.sleep(TimeUnit.MILLISECONDS.toMillis(50));
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
