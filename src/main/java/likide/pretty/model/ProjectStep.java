package likide.pretty.model;

import org.apache.maven.execution.ExecutionEvent.Type;
import org.apache.maven.plugin.MojoExecution;

public class ProjectStep {
	private final String key;
	private final String executionId;
	private final Type status;
	private final String goal;
	private final String phase;
	private final String groupId;
	private final String artifactId;
	
	public ProjectStep(MojoExecution execution, Type status) {
		this.key = execution.toString();
		this.executionId = execution.getExecutionId();
		this.status = status;
		this.goal = execution.getGoal();
		this.phase = execution.getLifecyclePhase();
		this.groupId = execution.getGroupId();
		this.artifactId = execution.getArtifactId();
	}

	public String getKey() {
		return key;
	}

	public String getExecutionId() {
		return executionId;
	}

	public Type getStatus() {
		return status;
	}

	public String getGoal() {
		return goal;
	}

	public String getPhase() {
		return phase;
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}
}