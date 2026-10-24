-- A position is an ordering hint the teacher controls, not part of what makes a row unique. A
-- deleted row is only hidden and keeps its position, and a reorder rewrites positions inside one
-- transaction, so the unique keys on (parent, order_index) go. Lists order by position, then
-- creation time.
-- The deployed constraints carry Hibernate-generated names (uk...), not the ones in V1, so each is
-- found by its table and columns.
DO $$
DECLARE
    c record;
BEGIN
    FOR c IN
        SELECT con.conrelid::regclass AS tbl, con.conname
        FROM pg_constraint con
        JOIN (VALUES ('modules', ARRAY['course_id', 'order_index']),
                     ('lessons', ARRAY['module_id', 'order_index']),
                     ('exercises', ARRAY['lesson_id', 'order_index'])) AS k (tbl, cols)
          ON con.conrelid = k.tbl::regclass
        WHERE con.contype = 'u'
          AND (SELECT array_agg(att.attname::text ORDER BY att.attname)
               FROM pg_attribute att
               WHERE att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)) = k.cols
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', c.tbl, c.conname);
    END LOOP;
END $$;

-- A delete used to hide only the row it was called on. Hide what it left visible under a hidden
-- parent, with the parent's deleted_at, so one timestamp finds a delete's whole subtree. Top down,
-- so each level sees the level above already hidden.
UPDATE modules m SET deleted_at = c.deleted_at
FROM courses c
WHERE m.course_id = c.id AND c.deleted_at IS NOT NULL AND m.deleted_at IS NULL;

UPDATE lessons l SET deleted_at = m.deleted_at
FROM modules m
WHERE l.module_id = m.id AND m.deleted_at IS NOT NULL AND l.deleted_at IS NULL;

UPDATE exercises e SET deleted_at = l.deleted_at
FROM lessons l
WHERE e.lesson_id = l.id AND l.deleted_at IS NOT NULL AND e.deleted_at IS NULL;

UPDATE questions q SET deleted_at = e.deleted_at
FROM exercises e
WHERE q.exercise_id = e.id AND e.deleted_at IS NOT NULL AND q.deleted_at IS NULL;
