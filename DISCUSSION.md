Run `lein deploy` each time a Repository containing a Leiningen project.clj file
is tagged or released.

This does not require any per repository configuration. It activates itself
whenever the following conditions are detected:

1. the Repository contains a project.clj file (users can configure exclude
   criteria - see Settings)
2. a Commit has been tagged with a semver compliant version.
3. GitHub linguist has determined that this project is a `Clojure` project
   (although in practice, the existince of a project.clj file is enough to do
   this)

This is ideal for cases where you want to share repository details, and
credentials, across your leiningen projects. When this skill runs, a profile is
added that will over-ride any `"releases"` deploy-repositories with the shared
one. You can also share repositories for reading dependencies but these will be
merged with any repositories already included in the checked in project.clj
file.

In addition to running `lein deploy`, this skill will also create a pending
GitHub CheckRun on the Commit, to indicate that a required deployment was
detected. The CheckRun will be updated with the status of the deployment once
the `lein deploy` has finished running.

Finally, this skill will raise a signal to notify other Skills that a new maven
artifact has been published. We use this to drive pull requests on other
projects that depend on this one.

## Why Tags?

The `lein-m2-deploy-skill` can work in combination with another skill that
manages a policy for tagging. For example, we have a skill that watches for a
set of GitHub Check runs passing (CI passing, cljfmt satisfied, clj-kondo with
zero violations, vulnerability scanning, etc.). If these checks pass then we
increment the version and Tag. Again, we try to use the same policy for Tagging
on all of our Leiningen projects.

The `lein-m2-deploy-skill` can of course be used with a manual tagging process.
However, two policies defining

-   "I tag Commits on my Clojure projects when ..."
-   "I deploy new versions of a library when ..." work really well together, and
    allow teams to scale their Clojure best practices cross a large set of
    Repositories.

### What about the project.clj version

We think that versions should be stored in git tags, not checked in to files
like project.clj. This skill does not try to keep the version in project.clj in
sync with the tag. The version in the tag over-rides whatever is in the
project.clj at deploy time.

### Excluding Projects

This same configuration of this skill can be applied across all of your
Clojure/Leiningen repositories. However, some Leiningen projects do not produce
libraries, and we can skip running `lein deploy` all together.

1.  Configure an "exclude" glob pattern - if this matches against the repo, we
    won't run lein deploy (or create a CheckRun). This is the preferred option
    and works for most users (e.g. `./Dockerfile` is a common way to recognize
    that this project does not produce a library)
2.  Configure a Repository filter to restrict this skill to act on only a subset
    of your repositories. This is mostly useful when first trying out the skill.
    In practice, the skill does nothing for non-Leiningen repositories and is
    safe to turn on for all your repos.

### How does this work?

This skill requires very little configuration (see settings). However, it does
require two important things:

1.  You must install the Atomist GitHub application to any GitHub orgs you have
    that contain Clojure projects.
2.  You must configure a target maven Repository Integration (e.g. like
    clojars).

These are both one time configurations. Any new projects should be ready to go.

## Feedback

This represents what we consider to be a good set of best practices but we are
looking for feedback. Some of the

-   we decided to not provide an option to deploy only GitHub "release" tags. Do
    teams need the additional semantic of a GitHub release?
-   we decided to allow the checked in project.clj version to stay out of sync
    with what is deployed. The important thing is that the tag and version
    deployed to maven are always in sync. Does it make sense of offer an option
    to sync these?
