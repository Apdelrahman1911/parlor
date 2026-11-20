# Parlor branch archive

`branches.tsv` records every remote branch tip in the 2026-11-20
audit snapshot. The archive commit's parents retain the recorded branch
histories. Existing branches remain available for active work and release
governance.

To recover a retired branch, look up its recorded object ID and run
`git branch <restored-name> <recorded-object-id>`. Use `git show <object-id>`
to inspect a recorded tip without checking it out.
