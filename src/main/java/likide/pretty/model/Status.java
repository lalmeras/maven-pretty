package likide.pretty.model;

public enum Status {
	PLANNED,
	BUILDING,
	SUCCESS,
	FAILED,
	SKIPPED;
	public boolean isFinished() {
		return this == SUCCESS || this == FAILED;
	}
}