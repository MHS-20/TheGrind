# Ski School Assignment

A ski school runs course types, each for one skill level and an age range, and only if at least `min_participants` students sign up. Students ask for a week.

```
course_types(id, name, skill_level, min_age, max_age, min_participants)
students(id, name, age, skill_level, requested_week)
assignments(course_id, student_id)
```

Write a procedure `assign_courses(p_week)` that empties `assignments` and fills it for the given week: a student goes to the course type with their skill level whose age range contains their age. A course that would have fewer than `min_participants` students is not run, and none of its students is assigned.

Run `schema.sql` (tables and example data), then `solution.sql`.

## Example

Week 1 has three young beginners (course 1 runs), one teenage beginner alone (course 2 does not run), two teenage advanced students (course 3 runs), and an adult with no matching course. Week 2 is ignored, and so is a leftover row from last week.

```
COURSE           STUDENT
---------------- --------
Kids beginners   Ada
Kids beginners   Bob
Kids beginners   Cat
Teens advanced   Eve
Teens advanced   Fay
```

## Approach

The obvious version is two nested cursors: for each course, count its matching students, and if there are enough, loop over them and insert each one. It works, and costs a count query per course plus a row-by-row insert.

All of it is one `INSERT ... SELECT`: join students to courses on level and age, and keep only the courses whose own join has `count(*) >= min_participants` (a grouped subquery with `HAVING`). The database does the counting and filtering in one pass.

`>=` matters: a course with exactly the minimum runs. Writing `>` silently cancels it.

Lesson: [a cursor loop vs one statement](../../../00_docs/16_sql_databases/16m_plsql_triggers.md#a-cursor-loop-vs-one-statement).
