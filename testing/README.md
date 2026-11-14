# Live link tests

Use Sniply Link Lab to prepare up to six public links per platform, then submit its compact JSON to **Actions → Test video links → Run workflow → suite_json**. This workflow runs the real Android service on an Android 15 emulator, including its embedded Python engine, native audio/video muxer and MediaStore gallery writer. It verifies a readable video track in the saved file; silent videos are reported as such.

The dashboard lives at https://sniply-link-lab-mrmaftahsa.verbalheather2.chatgpt.site and requires the owner's Site access. It never receives a GitHub token. Launch submission occurs on GitHub while signed in as the repository owner. Download the `Sniply-link-report` artifact after the run and import its ZIP into the dashboard.

## Limits and visibility

- Maximum 42 links, six per platform. Only HTTPS links on the supported platforms are accepted.
- Each URL gets three minutes. The app retains its normal quality choices and download limits; use short videos for faster checks. A full batch can take over an hour.
- A green workflow means the runner completed. The report records individual passes and failures.
- Submitted inputs may be visible in this public repository's workflow metadata or logs. Use public video URLs. Reports are retained as GitHub artifacts for seven days; downloaded videos are deleted from the emulator after testing.
- GitHub network addresses may be blocked by a platform even when a phone on a home/mobile network works. Results describe the tested app revision, engine and environment, not universal platform availability.
- The report checks service download, merging if needed, gallery saving and readable media tracks. It does not check the physical phone's gallery app, video playback UI or clipboard behavior.

The optional instrumented `LiveLinkTest` is skipped in normal test runs without a supplied URL. `scripts/live_links.py` validates batch input and coordinates isolated instrumented runs. It does not replace the app downloader with a server-side implementation.
