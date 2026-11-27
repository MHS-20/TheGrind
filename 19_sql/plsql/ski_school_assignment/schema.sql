CREATE TABLE course_types (
    id               NUMBER PRIMARY KEY,
    name             VARCHAR2(40) NOT NULL,
    skill_level      NUMBER NOT NULL,
    min_age          NUMBER NOT NULL,
    max_age          NUMBER NOT NULL,
    min_participants NUMBER NOT NULL
);
CREATE TABLE students (
    id             NUMBER PRIMARY KEY,
    name           VARCHAR2(40) NOT NULL,
    age            NUMBER NOT NULL,
    skill_level    NUMBER NOT NULL,
    requested_week NUMBER NOT NULL
);
CREATE TABLE assignments (
    course_id  NUMBER REFERENCES course_types,
    student_id NUMBER REFERENCES students,
    PRIMARY KEY (student_id)              -- a student takes one course
);

INSERT INTO course_types VALUES (1, 'Kids beginners',  1,  6, 10, 3);
INSERT INTO course_types VALUES (2, 'Teens beginners', 1, 11, 16, 2);
INSERT INTO course_types VALUES (3, 'Teens advanced',  2, 11, 16, 2);

INSERT INTO students VALUES (1, 'Ada',  7, 1, 1);
INSERT INTO students VALUES (2, 'Bob',  9, 1, 1);
INSERT INTO students VALUES (3, 'Cat', 10, 1, 1);
-- alone in course 2: course not run
INSERT INTO students VALUES (4, 'Dan', 12, 1, 1);
INSERT INTO students VALUES (5, 'Eve', 13, 2, 1);
INSERT INTO students VALUES (6, 'Fay', 15, 2, 1);
-- another week
INSERT INTO students VALUES (7, 'Gus',  8, 1, 2);
-- no course for adults
INSERT INTO students VALUES (8, 'Hal', 30, 2, 1);
-- stale row from last week
INSERT INTO assignments VALUES (1, 7);
COMMIT;
