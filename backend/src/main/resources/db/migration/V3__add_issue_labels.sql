-- V3__add_issue_labels.sql
-- Create the collection table used by Issue.labels

CREATE TABLE IF NOT EXISTS issue_labels (
    issue_id UUID NOT NULL,
    label VARCHAR(64) NOT NULL,
    CONSTRAINT pk_issue_labels PRIMARY KEY (issue_id, label),
    CONSTRAINT fk_issue_labels_issue
        FOREIGN KEY (issue_id)
        REFERENCES issues(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_issue_labels_issue
    ON issue_labels (issue_id);
