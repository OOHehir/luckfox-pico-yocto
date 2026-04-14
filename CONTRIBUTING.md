# Contributing

Thanks for looking at this project. It started as a personal Yocto BSP for
the LuckFox Pico Ultra W, but issues, PRs and hardware reports are welcome.

## Before you open a PR

1. **Build cleanly.** `bitbake luckfox-image-minimal` should complete from
   a fresh `build/` with only your change applied, and the resulting WIC
   should boot to multi-user on real hardware. If you don't have the
   board, say so in the PR — I'll test before merging.
2. **Pin versions.** Don't float a recipe to `master`/`HEAD`. Use a SHA so
   the build stays reproducible.
3. **Kernel/U-Boot patches live as `do_configure:prepend` or recipe
   patches**, not as edits to fetched source. The fetched trees are
   mirrors of upstream and must stay untouched so the build is
   reproducible for everyone.
4. **Keep secrets out of the tree.** WiFi credentials, API tokens and the
   like belong in `conf/local.conf` (gitignored), not in recipes. See the
   `wpa-supplicant.bbappend` pattern for how to plumb a `local.conf`
   variable into an installed file.
5. **One logical change per commit.** Commit messages explain the *why*.
   The existing `git log --oneline` is a reasonable style reference.

## Scope

In scope:

- Bringing up hardware already on the board (camera CSI, watchdog,
  thermal, OP-TEE, RNG, SARADC — see `docs/ROADMAP.md`).
- Bug fixes to existing recipes.
- New image variants (`luckfox-image-ipcam`, etc.) living alongside
  `luckfox-image-minimal`.
- CI (GitHub Actions smoke build with cached sstate is on the wishlist).

Probably out of scope — open an issue first:

- Supporting additional LuckFox boards (would pull the layer away from
  its single-board focus; better as a sibling machine config).
- Switching Yocto release from scarthgap.
- Replacing the SDK kernel with mainline — interesting but a large
  project; worth its own branch rather than PR.

## Reporting issues

Include:

- Exact git SHA of this repo.
- Host distribution + Yocto build host setup (native or container).
- Full `bitbake` error output, not just the last line.
- For runtime bugs: `dmesg`, `systemctl --failed`, the image build
  timestamp (see `/etc/os-release` on the board).

## Licensing of contributions

By contributing you agree that your contributions are licensed under the
same MIT license as the rest of the repository (`LICENSE`). If your
contribution brings in third-party code under a different license, note
it in the PR and update `THIRD_PARTY_LICENSES.md`.
