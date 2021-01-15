# `@atomist/lein-m2-deploy`

## Background

Watch for Tags on repositories that contain Leiningen project.clj files.  
The following conditions must be true:

-   a git Tag ref has been created
-   the root of the Repository contains a project.clj file
-   GitHub has marked this Repo as containing `Clojure` code
-   the Skill can be configured to skip Repositories that contain specified
    files (like a `Dockerfile`)

Upon detection, deploy the clojure library to a m2 repo, such as
[clojars](https://clojars.org). Use the following procedure to perform this
action.

-   clone the Commit using the Atomist GitHub App Installation token (this is
    the token used for all github related operations listed below)
-   add a pending GitHub CheckRun named "lein m2 deploy" to the Commit
-   use `git describe` to fetch the last semver tag, increment it, and then tag
    the Commit (unsigned) with the new version
-   temporarily adjust the local project.clj with the new version and run
    `lein deploy`
-   update the "lein m2 deploy" CheckRun based on whether the artifact was
    successfully published
-   this skill over-rides any `"releases"` deploy-repository configured in the
    project.clj, with the one selected when configuring this skill. Repositories
    for downloading dependencies can also be specified here but they are always
    added to the ones already configured in a project.clj. Only the release
    repository for deployments is over-ridden.

If the deployment is successful, this skill also tracks the new maven artifact,
and its relationship to a Commit, in the Atomist graph. Other skills can watch
for this new artifact and choose to automatically update their own dependencies.

This skill also allows users to restrict its action to a filter set of
repositories in a GitHub organization; however, the skill is design to automate
deployment for any leiningen project, now or in the future, that contains a
clojure library.

### Subscriptions

#### tag-with-content

```
When tags are created after all checks have passed on a Commit then run the Leiningen deploy task.
```

When a new CheckRun

if skill is configured with `tag?` config parameter set to true

```
(get-config-value "tag?" false true)
```

and a tag has been pushed in this change

```

```

and there's a project.clj file but no Docker files

```
(get-config-value "path-exists" ["project.clj"] ?path-exists)
(get-config-value "path-not-exists" ["Dockerfile" "docker/Dockerfile" "docker/Dockerfile.gcr"] ?path-not-exists)
```

### no updates to project.clj

Although many skills do push updates to Repo contents, our default
implementation of this skill does not update `project.clj` with new versions. We
encourage the use of git tags as being the sole source of truth for a released
version. To that end, this skill can be configured to either only build tagged
Commits, or to create tags, when the release conditions are met, and then use
the tag version for the deployment. We edit the project.clj locally, so that we
can use the `lein deploy` task unaltered. However, we allow the checked in
project.clj version to remain permanently out of sync with the deployed version.
If you want to see the most recently deployed version, use `git describe`
instead.

## Should we sign either jars or tags?

I think both come with similar requirements. For tags, we might determine that
users will want to tag releases themselves, so this is probably not an issue.
The tag is just part of the subscription in that case and the use case could be
to provide a filter so that skill only sees signed tags from a set of trusted
signers.

For jar signing, the clojure community appears to be a long way from being able
to rely on this for any additional security. In most of our projects, the number
of jars that are signed by a public keys that we could discover and
realistically verify is quite low (about 10% of the libraries we actually use).
The open source maintainers in the clojure world often sign their jars but then
don't upload their signatures. And no one seems to care about whether the jars
in their classpath can be verified. Perhaps the shift is to trust `group`s in
clojars because only people with deploy tokens for users who are permitted to
publish to those groups can create those artifacts.

However, it is possible that if it was easier to consistently sign jars, we
could ratchet up this trust model by helping contributors to maintain and
consistently use their signing keys. You might even imagine a corresponding
CheckRun skill that verifies signatures for a selected set of artifacts, or
artifact groups.

However, I'm not sure if a signing key managed by a Cloud Service would ever
make sense to OSS contributors. If the signing service was open-source and
managed by a non-commercial entity, like clojars, it would be different.  
However, it would have to be a separate service from atomist.com, wouldn't it?

If there were an open file-signing service (and file-verification service),
protected by something like OAuth, where users could manage their signing keys,
but still grant authorization to services like Atomist to sign jars on their
behalf, then we could maybe start to offer workflows that include file signing.
However, I still think this would have to be a dot-org.

One final idea is to offer signing using an identity we own (e.g
lein-m2-deploy-skill@atomist.com). What would it mean for users to trust this
skill? Project maintainers could sign attestations saying they trusted
lein-m2-deploy-skill@atomist.com, but is there any value in that? Atomist users
could easily trick lein-m2-deploy-skill@atomist.com into signing pretty much any
artifact (e.g. org.clojure/clojure version 10.4) by just presenting it with a
fake project.clj file. Access to the `org.clojure` group on clojars is still the
only real protection there. The only thing trusting `lein-m2-deploy` skill really
does is ensure that bad actors with access to an `org.clojure` deploy token,
can't upload a bad clojure jar without leaving behind a trace in git. Is there
value in that?

-   [link to one signing as a service company](https://about.signpath.io/documentation/signing-code#)
-   [another company doing pki as a service](https://www.keyfactor.com/business-need/accelerate-devops-security/)
-   [this article](https://latacora.micro.blog/2019/07/16/the-pgp-problem.html)
    is highly critical of GPG but maybe not so much about GPG for code signing.
    Although even there, the author suggests a tool like
    [minisign](https://jedisct1.github.io/minisign/) if you're signing files.
-   [presentation on different signing formats used across different platforms](https://cabforum.org/wp-content/uploads/7-code-signing-formats.pdf).
    All of these, except perhaps GPG(PGP), rely on PKI infrastructure and CAs.
    Java signing (with jarsigner and keytool), for example, is wholly reliant on
    certificate signing requests, and CAs.
-   [a high speed elliptic curve signing algo (Ed25519)](https://ed25519.cr.yp.to/ed25519-20110926.pdf)

---

Created by [Atomist][atomist]. Need Help? [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ "Atomist"
[slack]: https://join.atomist.com/ "Atomist Community Slack"
