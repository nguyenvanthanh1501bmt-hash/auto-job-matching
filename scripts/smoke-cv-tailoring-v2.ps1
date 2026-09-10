param(
    [Parameter(Mandatory = $true)]
    [string]$UserAccessToken,

    [Parameter(Mandatory = $true)]
    [string]$AdminAccessToken,

    [Parameter(Mandatory = $true)]
    [string]$CandidateProfileId,

    [Parameter(Mandatory = $false)]
    [string]$NormalizedJobId,

    [Parameter(Mandatory = $false)]
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw "ASSERTION FAILED: $Message"
    }
}

function Is-Blank {
    param(
        [object]$Value
    )

    if ($null -eq $Value) {
        return $true
    }

    return [string]::IsNullOrWhiteSpace(
        [string]$Value
    )
}

function Print-Step {
    param(
        [string]$Message
    )

    Write-Host ""
    Write-Host "=================================================="
    Write-Host $Message
    Write-Host "=================================================="
}

$base = $BaseUrl.TrimEnd("/")

$userHeaders = @{
    Authorization = "Bearer $UserAccessToken"
}

$adminHeaders = @{
    Authorization = "Bearer $AdminAccessToken"
}

Print-Step "1. Check current candidate embedding"

$beforeEmbedding = $null

try {
    $beforeEmbedding = Invoke-RestMethod `
        -Method Get `
        -Uri "$base/api/admin/candidate-embeddings/$CandidateProfileId" `
        -Headers $adminHeaders

    Write-Host "Current textVersion : $($beforeEmbedding.textVersion)"
    Write-Host "Current status      : $($beforeEmbedding.status)"
    Write-Host "Current textHash    : $($beforeEmbedding.textHash)"
}
catch {
    Write-Host "No existing candidate embedding was returned."
    Write-Host "This is acceptable before the rebuild."
}

Print-Step "2. Force rebuild candidate embedding as candidate-text-v2"

$rebuiltEmbedding = Invoke-RestMethod `
    -Method Post `
    -Uri "$base/api/admin/candidate-embeddings/$CandidateProfileId/rebuild?force=true" `
    -Headers $adminHeaders

Assert-True `
    ($rebuiltEmbedding.candidateProfileId -eq $CandidateProfileId) `
    "Rebuilt embedding belongs to another candidate."

Assert-True `
    ($rebuiltEmbedding.textVersion -eq "candidate-text-v2") `
    "Expected candidate-text-v2 but got '$($rebuiltEmbedding.textVersion)'."

Assert-True `
    ($rebuiltEmbedding.status -eq "READY") `
    "Expected READY embedding but got '$($rebuiltEmbedding.status)'."

Assert-True `
    (-not (Is-Blank $rebuiltEmbedding.embeddingVersion)) `
    "embeddingVersion is blank."

Assert-True `
    (-not (Is-Blank $rebuiltEmbedding.textHash)) `
    "textHash is blank."

Assert-True `
    ($rebuiltEmbedding.dimension -gt 0) `
    "Embedding dimension is invalid."

Assert-True `
    ($rebuiltEmbedding.normalized -eq $true) `
    "Embedding must be normalized."

Write-Host "PASS"
Write-Host "textVersion      : $($rebuiltEmbedding.textVersion)"
Write-Host "embeddingVersion : $($rebuiltEmbedding.embeddingVersion)"
Write-Host "textHash         : $($rebuiltEmbedding.textHash)"
Write-Host "dimension        : $($rebuiltEmbedding.dimension)"

if ($null -ne $beforeEmbedding) {

    if (
        $beforeEmbedding.textVersion -eq "candidate-text-v1" -and
        $beforeEmbedding.textHash -eq $rebuiltEmbedding.textHash
    ) {
        Write-Warning `
            "v1 and v2 textHash are identical. " +
            "This can be valid only when the profile has no v2-only " +
            "license/language signals."
    }
}

Print-Step "3. Verify latest embedding is now v2"

$latestEmbedding = Invoke-RestMethod `
    -Method Get `
    -Uri "$base/api/admin/candidate-embeddings/$CandidateProfileId" `
    -Headers $adminHeaders

Assert-True `
    ($latestEmbedding.textVersion -eq "candidate-text-v2") `
    "Latest embedding is not candidate-text-v2."

Assert-True `
    ($latestEmbedding.status -eq "READY") `
    "Latest embedding is not READY."

Write-Host "PASS"
Write-Host "Latest textVersion : $($latestEmbedding.textVersion)"
Write-Host "Latest status      : $($latestEmbedding.status)"

Print-Step "4. Force production matching with v2 embedding"

$matching = Invoke-RestMethod `
    -Method Post `
    -Uri "$base/api/matching/candidates/$CandidateProfileId?force=true" `
    -Headers $userHeaders

Assert-True `
    ($matching.candidateProfileId -eq $CandidateProfileId) `
    "Matching response belongs to another candidate."

Assert-True `
    (-not (Is-Blank $matching.candidateEmbeddingId)) `
    "Matching did not use a candidate embedding."

Assert-True `
    (-not (Is-Blank $matching.rankingVersion)) `
    "Matching rankingVersion is blank."

Write-Host "PASS"
Write-Host "candidateEmbeddingId : $($matching.candidateEmbeddingId)"
Write-Host "rankingVersion       : $($matching.rankingVersion)"
Write-Host "retrievedCount       : $($matching.retrievedCount)"
Write-Host "loadedJobCount       : $($matching.loadedJobCount)"
Write-Host "matchedCount         : $($matching.matchedCount)"
Write-Host "reusedExisting       : $($matching.reusedExisting)"

if ($matching.reusedExisting -eq $true) {
    Write-Warning `
        "force=true returned reusedExisting=true. " +
        "Check HybridMatchingService force semantics."
}

Print-Step "5. Resolve target job for tailoring"

$targetJobId = $NormalizedJobId

if (Is-Blank $targetJobId) {

    $matchingResults = @(
        $matching.results
    )

    Assert-True `
        ($matchingResults.Count -gt 0) `
        "No matched job is available. Supply -NormalizedJobId manually or ensure jobs are embedded."

    $targetJobId =
        $matchingResults[0].normalizedJobId
}

Assert-True `
    (-not (Is-Blank $targetJobId)) `
    "Target normalizedJobId is blank."

Write-Host "Target job: $targetJobId"

Print-Step "6. Analyze CV tailoring"

$analysis = Invoke-RestMethod `
    -Method Post `
    -Uri "$base/api/cv-tailoring/candidates/$CandidateProfileId/jobs/$targetJobId/analyze" `
    -Headers $userHeaders

Assert-True `
    (-not (Is-Blank $analysis.analysisId)) `
    "Analyze response has no analysisId."

Assert-True `
    ($analysis.candidateProfileId -eq $CandidateProfileId) `
    "Analysis belongs to another candidate."

Assert-True `
    ($analysis.normalizedJobId -eq $targetJobId) `
    "Analysis belongs to another job."

Assert-True `
    ($analysis.currentMatch.rankingVersion -eq $matching.rankingVersion) `
    "Analyze rankingVersion differs from the current matching run."

$suggestions = @(
    $analysis.suggestions
)

$gaps = @(
    $analysis.gaps
)

$rewriteCount = @(
    $suggestions |
        Where-Object {
            $_.type -eq "REWRITE"
        }
).Count

$emphasizeCount = @(
    $suggestions |
        Where-Object {
            $_.type -eq "EMPHASIZE"
        }
).Count

$gapCount = $gaps.Count

Write-Host "PASS"
Write-Host "analysisId      : $($analysis.analysisId)"
Write-Host "REWRITE         : $rewriteCount"
Write-Host "EMPHASIZE       : $emphasizeCount"
Write-Host "GAP_WARNING     : $gapCount"

Print-Step "7. Select one server-generated applicable suggestion"

$applicableSuggestions = @(
    $suggestions |
        Where-Object {
            $_.type -eq "REWRITE" -or
            $_.type -eq "EMPHASIZE"
        }
)

$acceptedSuggestionIds = @()

if ($applicableSuggestions.Count -gt 0) {

    $selectedSuggestion =
        $applicableSuggestions[0]

    Assert-True `
        (-not (Is-Blank $selectedSuggestion.id)) `
        "Selected suggestion has no id."

    $acceptedSuggestionIds = @(
        $selectedSuggestion.id
    )

    Write-Host "Applying suggestion:"
    Write-Host "id      : $($selectedSuggestion.id)"
    Write-Host "type    : $($selectedSuggestion.type)"
    Write-Host "section : $($selectedSuggestion.section)"
    Write-Host "sourceId: $($selectedSuggestion.sourceId)"
}
else {
    Write-Host `
        "No REWRITE/EMPHASIZE suggestion is available."

    Write-Host `
        "Preview will run with acceptedSuggestionIds=[] " +
        "to verify the matching/embedding path."
}

Print-Step "8. Preview using analysisId + acceptedSuggestionIds only"

$previewBody = @{
    analysisId = $analysis.analysisId
    acceptedSuggestionIds = $acceptedSuggestionIds
} | ConvertTo-Json -Depth 10

$preview = Invoke-RestMethod `
    -Method Post `
    -Uri "$base/api/cv-tailoring/candidates/$CandidateProfileId/jobs/$targetJobId/preview" `
    -Headers $userHeaders `
    -ContentType "application/json" `
    -Body $previewBody

Assert-True `
    ($preview.analysisId -eq $analysis.analysisId) `
    "Preview analysisId differs from Analyze."

Assert-True `
    ($preview.candidateProfileId -eq $CandidateProfileId) `
    "Preview belongs to another candidate."

Assert-True `
    ($preview.normalizedJobId -eq $targetJobId) `
    "Preview belongs to another job."

Assert-True `
    ($preview.rankingVersion -eq $matching.rankingVersion) `
    "Preview rankingVersion differs from current matching run."

Assert-True `
    (-not (Is-Blank $preview.temporaryEmbeddingVersion)) `
    "Temporary embedding version is blank."

$validStatuses = @(
    "MATCHED",
    "NOT_RETRIEVED",
    "NOT_MATCHED"
)

Assert-True `
    ($validStatuses -contains $preview.before.status) `
    "Unexpected Before status '$($preview.before.status)'."

Assert-True `
    ($validStatuses -contains $preview.after.status) `
    "Unexpected After status '$($preview.after.status)'."

if (
    $preview.after.status -eq "NOT_RETRIEVED" -or
    $preview.after.status -eq "NOT_MATCHED"
) {
    Assert-True `
        ($null -eq $preview.after.finalScore) `
        "After score must be null for $($preview.after.status)."
}

if ($acceptedSuggestionIds.Count -eq 0) {

    Assert-True `
        (@($preview.appliedSuggestionIds).Count -eq 0) `
        "Preview applied an unexpected suggestion."
}
else {
    Assert-True `
        (@($preview.appliedSuggestionIds) -contains $acceptedSuggestionIds[0]) `
        "Selected server-side suggestion was not applied."
}

Write-Host "PASS"

Print-Step "9. Smoke result"

Write-Host "Candidate : $CandidateProfileId"
Write-Host "Job       : $targetJobId"

Write-Host ""
Write-Host "Persistent embedding:"
Write-Host "  textVersion      : $($latestEmbedding.textVersion)"
Write-Host "  embeddingVersion : $($latestEmbedding.embeddingVersion)"
Write-Host "  status           : $($latestEmbedding.status)"

Write-Host ""
Write-Host "Matching:"
Write-Host "  rankingVersion   : $($matching.rankingVersion)"
Write-Host "  matchedCount     : $($matching.matchedCount)"

Write-Host ""
Write-Host "Tailoring:"
Write-Host "  analysisId       : $($analysis.analysisId)"
Write-Host "  appliedCount     : $(@($preview.appliedSuggestionIds).Count)"
Write-Host "  temporaryVersion : $($preview.temporaryEmbeddingVersion)"
Write-Host "  beforeStatus     : $($preview.before.status)"
Write-Host "  afterStatus      : $($preview.after.status)"
Write-Host "  beforeScore      : $($preview.before.finalScore)"
Write-Host "  afterScore       : $($preview.after.finalScore)"

Write-Host ""
Write-Host "=================================================="
Write-Host "CV TAILORING V2 SMOKE: PASS"
Write-Host "=================================================="