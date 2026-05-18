-- Tasks indexed by assignee, for operator-scoped queries
-- (TaskRepository.findAssignedTo). A row is inserted on TaskAssigned and
-- updated in place on TaskCompleted / TaskCancelled so historical assignments
-- remain queryable (the mobile picker shows "my recent tasks" in addition to
-- "my active tasks"). When reassign lands, the row's user_id is overwritten.

CREATE TABLE IF NOT EXISTS task_by_assignee (
  task_id      UUID NOT NULL,
  user_id      UUID NOT NULL,
  state        VARCHAR(50) NOT NULL,
  assigned_at  TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (task_id)
);

CREATE INDEX IF NOT EXISTS task_by_assignee_user_state_idx
  ON task_by_assignee (user_id, state);
