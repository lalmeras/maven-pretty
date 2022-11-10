package likide.pretty.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.maven.execution.ExecutionEvent.Type;
import org.apache.maven.project.MavenProject;

import likide.pretty.Constants;

public class ProjectStatus {
	private final MavenProject mavenProject;
	private final Status status;
	private final List<ProjectStep> previousSteps;
	private final ProjectStep currentStep;
	
	public ProjectStatus(ProjectStatus currentStatus, ProjectStatus lastStatus) {
		this(currentStatus.mavenProject, currentStatus.status, currentStatus.currentStep, lastStatus);
	}
	
	public ProjectStatus(MavenProject mavenProject, Status status, ProjectStep currentStep, ProjectStatus lastStatus) {
		this.mavenProject = mavenProject;
		this.status = status;
		this.currentStep = currentStep;
		if (lastStatus != null) {
			List<ProjectStep> steps = new ArrayList<>();
			if(lastStatus.previousSteps != null && !lastStatus.previousSteps.isEmpty()) {
				steps.addAll(lastStatus.previousSteps);
			}
			if (lastStatus.currentStep != null && !Type.MojoStarted.equals(lastStatus.currentStep.getStatus())) {
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
			return Constants.TERM_ESCAPE + Constants.TERM_BOLD_RED + "✘" + Constants.TERM_RESET;
		case PLANNED:
			return Constants.TERM_ESCAPE + Constants.TERM_BOLD_BLUE + "⧖" + Constants.TERM_RESET;
		case SUCCESS:
			return Constants.TERM_ESCAPE + Constants.TERM_BOLD_GREEN + "✔" + Constants.TERM_RESET;
		default:
			throw new IllegalStateException();
		}
	}
	
	public String dot(int clock) {
		switch ((clock/10)%3) {
		case 0:
			return Constants.TERM_ESCAPE + Constants.TERM_BOLD_YELLOW + "․" + Constants.TERM_RESET;
		case 1:
			return Constants.TERM_ESCAPE + Constants.TERM_BOLD_YELLOW + "‥" + Constants.TERM_RESET;
		case 2:
			return Constants.TERM_ESCAPE + Constants.TERM_BOLD_YELLOW + "…" + Constants.TERM_RESET;
		default:
			throw new IllegalStateException();
		}
	}
	
	public String lastSteps() {
		if (previousSteps == null) {
			return "";
		}
		return previousSteps.stream().map(i -> i.getGoal()).collect(Collectors.joining(", ", "(", ")")) + " ";
	}
	
	public String lastPhases(int clock) {
		if (previousSteps == null) {
			return "";
		}
		List<String> phases = previousSteps.stream().map(i -> phaseOrPlugin(i)).distinct().collect(Collectors.toList());
		if (Status.SUCCESS.equals(status)) {
			if (phases.isEmpty()) {
				return "";
			} else if (phases.size() == 1) {
				return Constants.TERM_ESCAPE + Constants.TERM_GREY + phases.get(0) + Constants.TERM_RESET;
			} else {
				return String.format(Constants.TERM_ESCAPE + Constants.TERM_GREY + "%s … %s", phases.get(0), phases.get(phases.size() - 1) + Constants.TERM_RESET);
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append(phases.stream().collect(Collectors.joining(", ")));
		if (currentStep != null && !phases.isEmpty()) {
			String lastBeforeCurrent = phases.stream().skip(phases.size() - 1l).findFirst().orElse(null);
			if (lastBeforeCurrent != null && lastBeforeCurrent.equals(currentStep.getPhase())) {
				// last phase is still performing
				sb.append(dot(clock));
			} else if (lastBeforeCurrent != null && !lastBeforeCurrent.equals(currentStep.getPhase())) {
				sb.append(", " + currentStep.getPhase() + dot(clock));
			} else if (lastBeforeCurrent == null) {
				sb.append(currentStep.getPhase() + dot(clock));
			}
		} else if (currentStep != null) {
			sb.append(currentStep.getPhase() + dot(clock));
		}
		return sb.toString();
	}
	
	public String phaseOrPlugin(ProjectStep execution) {
		return Optional.ofNullable(execution.getPhase()).orElseGet(() -> shortGoal(execution));
	}
	
	public String shortGoal(ProjectStep execution) {
		if (execution.getArtifactId().startsWith("maven-") && execution.getArtifactId().endsWith("-plugin")) {
			return String.format("%s:%s", execution.getArtifactId().substring("maven-".length(), execution.getArtifactId().length() - "-plugin".length()), execution.getGoal());
		} else {
			return String.format("%s:%s", execution.getArtifactId(), execution.getGoal());
		}
	}

	public MavenProject getMavenProject() {
		return mavenProject;
	}

	public Status getStatus() {
		return status;
	}

	public List<ProjectStep> getPreviousSteps() {
		return previousSteps;
	}

	public ProjectStep getCurrentStep() {
		return currentStep;
	}
}