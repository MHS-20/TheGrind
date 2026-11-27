SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF
SET LINESIZE 120 PAGESIZE 100

CREATE OR REPLACE PROCEDURE assign_courses(p_week NUMBER) IS
BEGIN
    DELETE FROM assignments;

    INSERT INTO assignments (course_id, student_id)
    SELECT c.id, s.id
    FROM students s
    JOIN course_types c
      ON c.skill_level = s.skill_level AND s.age BETWEEN c.min_age AND c.max_age
    WHERE s.requested_week = p_week
      AND c.id IN (SELECT c2.id
                   FROM course_types c2
                   JOIN students s2
                     ON s2.skill_level = c2.skill_level AND s2.age BETWEEN c2.min_age AND c2.max_age
                   WHERE s2.requested_week = p_week
                   GROUP BY c2.id, c2.min_participants
                   HAVING count(*) >= c2.min_participants);
END;
/

EXEC assign_courses(1)
COLUMN course FORMAT A16
COLUMN student FORMAT A8
SELECT c.name AS course, s.name AS student
FROM assignments a JOIN course_types c ON c.id = a.course_id JOIN students s ON s.id = a.student_id
ORDER BY c.id, s.id;
