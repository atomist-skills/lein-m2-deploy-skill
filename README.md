# `@atomist/lein-m2-deploy-skill`

## Background

Watch for Commits where all of the following conditions are true:

-   Commit was made to the default branch of a Repository in GitHub
-   a configurable set of GitHub CheckRuns has passed (configurable on the Skill
    config page)
-   the root of the Repository contains a project.clj file
-   the Repository does not contain a Dockerfile, or a docker/Dockerfile

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

If the deployment is successful, this skill also tracks the new maven artifact,
and it's relationship to a the Commit, in the Atomist graph. Other skills can
watch for this new artifact and choose to automatically update their own
dependencies.

This skill also allows users to restrict its action to a filter set of
repositories in a GitHub organization; however, the skill is design to automate
deployment for any leiningen project, now or in the future, that contains a
clojure library.

## Todo

-   [ ] this only supports patch versioning today.  
         If we respond to tags instead, we can put versioning back into the hands
        of the developer.
-   [ ] we should document that we don't care what version is in the checked in
        project.clj (which is a best practice in our opinion)
-   [ ] we should probably mention that this skill does not deploy snapshots,
        and will therefore never use the `snapshots` repository. This is
        `release` only (also a best practice).
-   [ ] we should also document the leiningen convention of configuring a
        `releases` repo in either `:repositories` or `:deploy-repositories`.  
         [Leiningen documentation](https://github.com/technomancy/leiningen/blob/master/doc/DEPLOY.md)
        talks a lot about how to add credentials here but the beauty of this skill
        is that we can inject the credentials from one place. However, it seems to
        me we should also automatically update the project.clj to use our convention
        before running. Why would leave a potential inconsistency between project.clj
        and skill config if we can check it?
-   [ ] leiningen supports a strange property named `:deploy-branches`, which
        could breaks this skill if the local project.clj has this property and
        the default branch is not included in it
-   [ ] this skill will fail if the project.clj does not have a `:license`,
        `:description` and `:url` We should make this error more explicit.
-   [ ] leiningen also supports https proxies here but Skill users should not
        need these unless we're trying to publish to a private registry
        accessibly only through a proxy. We probably want to ignore proxy
        settings added by developers that are behind a proxy, and where the
        skill does not have the same restriction.
-   [ ] whether or not we sign is determined by the `:sign-releases` option in
        the repository opts map. Should we always set this to false?
-   [ ] the `:gpg-key` and `:passphrase` are the only real signing options, and
        supporting these would require that we address whether or not we're will
        to manage an identity for the skill configurer. See below.

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
consistently use their signing keys. You might even imagine a correspond
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
only real protection there. The only thing trusting lein-m2-deploy-skill really
does is ensure that bad actors with access to an `org.clojure` deploy token,
can't upload a bad clojure jar without leaving behind a trace in git. Is there
value in that?

* [link to one signing as a service company](https://about.signpath.io/documentation/signing-code#)
* [another company doing pki as a service](https://www.keyfactor.com/business-need/accelerate-devops-security/)
* [this article](https://latacora.micro.blog/2019/07/16/the-pgp-problem.html) is highly critical of GPG but maybe not so much about GPG for code signing.  Although even there, the author suggests a tool like [minisign](https://jedisct1.github.io/minisign/) if you're signing files.
---

Created by [Atomist][atomist]. Need Help? [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ "Atomist - How Teams Deliver Software"
[slack]: https://join.atomist.com/ "Atomist Community Slack"
