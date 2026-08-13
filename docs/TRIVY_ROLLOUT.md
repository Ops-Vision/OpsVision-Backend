Goal
- Make the organization use a single reusable Trivy workflow so every repo can call it without changing `devops/scanners/trivy.yaml` in each repo.

Options

1) Central templates repo (recommended)
- Create a repo in your org (example `org-actions` or `.github`) and commit the reusable workflow into `.github/workflows/trivy-scan-reusable.yml` and tag a release `v1`.
- Add the lightweight caller workflow to each repo: `.github/workflows/call-trivy.yml` (example included in this repo).

2) Use this repository as the template source
- Repositories can reference the reusable workflow here:

  uses: <ORG>/OpsVision-Infrastructure/.github/workflows/trivy-scan-reusable.yml@main

Rollout helper (PowerShell)

Example script to add the caller workflow to many repos using `gh`:

```powershell
# Requires gh cli and authentication
$org = "myorg"
$repos = @("repo-a","repo-b")

foreach ($r in $repos) {
  gh repo clone "$org/$r"
  Set-Location $r
  mkdir -Force .github\workflows
  Copy-Item ..\call-trivy-workflow.yml .github\workflows\call-trivy.yml
  git add .github\workflows\call-trivy.yml
  git commit -m "Add org Trivy reusable workflow call"
  git push origin HEAD
  Set-Location ..
}
```

Security notes
- Reusable workflows run with the permissions of the calling repository. Ensure organization policies and admins approve the template.
