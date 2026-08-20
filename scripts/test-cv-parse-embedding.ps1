$ErrorActionPreference = "Stop"

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

[Console]::InputEncoding = $Utf8NoBom
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom

if (Get-Command chcp.com -ErrorAction SilentlyContinue) {
    & chcp.com 65001 | Out-Null
}


function Invoke-Utf8JsonRequest {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("GET", "POST", "PUT", "PATCH", "DELETE")]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [string]$Uri
    )

    $response = Invoke-WebRequest `
        -UseBasicParsing `
        -Method $Method `
        -Uri $Uri `
        -Headers @{
            Accept = "application/json"
        }

    $stream = $response.RawContentStream

    if ($null -eq $stream) {
        throw "Response from $Uri does not expose RawContentStream"
    }

    if ($stream.CanSeek) {
        $stream.Position = 0
    }

    $memory = New-Object System.IO.MemoryStream

    try {
        $stream.CopyTo($memory)
        $bytes = $memory.ToArray()
    }
    finally {
        $memory.Dispose()
    }

    $json = [System.Text.Encoding]::UTF8.GetString(
        $bytes
    )

    $json = $json.TrimStart(
        [char]0xFEFF
    )

    if ([string]::IsNullOrWhiteSpace($json)) {
        return $null
    }

    try {
        return (
            $json |
            ConvertFrom-Json
        )
    }
    catch {
        throw "Response from $Uri is not valid JSON. Body: $json"
    }
}


$RepoRoot = Split-Path -Parent $PSScriptRoot

$CvPath = "D:\test-data\cv2.pdf"

$BaseUrl = "http://localhost:8080"

$OutputDir = Join-Path `
    $RepoRoot `
    "tmp\cv-debug"


if (-not (Test-Path $CvPath)) {
    throw "CV file not found: $CvPath"
}


New-Item `
    -ItemType Directory `
    -Force `
    -Path $OutputDir |
    Out-Null


Write-Host ""
Write-Host "========================================"
Write-Host "1. CHECK INPUT"
Write-Host "========================================"

Write-Host "CV:"
Write-Host $CvPath


$file = Get-Item $CvPath

Write-Host ""
Write-Host "Size:"
Write-Host $file.Length


Write-Host ""
Write-Host "========================================"
Write-Host "2. CHECK AUTOJOB APP"
Write-Host "========================================"

try {
    $health = Invoke-Utf8JsonRequest `
        -Method GET `
        -Uri "$BaseUrl/actuator/health"

    $health |
        ConvertTo-Json -Depth 20
}
catch {
    Write-Host ""
    Write-Host "Cannot reach autojob-app."
    Write-Host ""
    Write-Host "Check:"
    Write-Host "docker compose --env-file .env ps"
    Write-Host "docker compose --env-file .env logs --tail=100 autojob-app"

    throw
}


Write-Host ""
Write-Host "========================================"
Write-Host "3. UPLOAD CV"
Write-Host "========================================"


$uploadJson = & curl.exe `
    -sS `
    -X POST `
    "$BaseUrl/api/cvs" `
    -F "file=@$CvPath;type=application/pdf"


if ($LASTEXITCODE -ne 0) {
    throw "curl upload failed"
}


Write-Host ""
Write-Host "Upload response:"
Write-Host $uploadJson


try {
    $upload = (
        $uploadJson |
        ConvertFrom-Json
    )
}
catch {
    throw "Upload response is not valid JSON: $uploadJson"
}


$rawCvId = $upload.id


if ([string]::IsNullOrWhiteSpace($rawCvId)) {
    throw "Upload response does not contain id"
}


Write-Host ""
Write-Host "rawCvId:"
Write-Host $rawCvId


Write-Host ""
Write-Host "========================================"
Write-Host "4. PARSE CV"
Write-Host "========================================"


try {
    $parsed = Invoke-Utf8JsonRequest `
        -Method POST `
        -Uri "$BaseUrl/api/cvs/$rawCvId/parse"
}
catch {
    Write-Host ""
    Write-Host "Parse failed."
    Write-Host ""
    Write-Host "Check:"
    Write-Host "docker compose --env-file .env logs --tail=200 autojob-app"
    Write-Host "docker compose --env-file .env logs --tail=200 cv-parser-service"

    throw
}


$parsedJson = (
    $parsed |
    ConvertTo-Json -Depth 100
)


$parsedFile = Join-Path `
    $OutputDir `
    "parsed-cv.json"


$parsedJson |
    Set-Content `
        -Path $parsedFile `
        -Encoding UTF8


Write-Host ""
Write-Host "========================================"
Write-Host "5. PARSED CV - FULL JSON"
Write-Host "========================================"
Write-Host ""

Write-Host $parsedJson


Write-Host ""
Write-Host "========================================"
Write-Host "6. PARSED CV - IMPORTANT FIELDS"
Write-Host "========================================"


Write-Host ""
Write-Host "Full name:"
Write-Host $parsed.fullName


Write-Host ""
Write-Host "Headline:"
Write-Host $parsed.headline


Write-Host ""
Write-Host "Professional summary:"
Write-Host $parsed.professionalSummary


Write-Host ""
Write-Host "Career objective:"
Write-Host $parsed.careerObjective


Write-Host ""
Write-Host "Seniority:"
Write-Host $parsed.seniority


Write-Host ""
Write-Host "Experience years:"
Write-Host $parsed.experienceYears


Write-Host ""
Write-Host "Highest education:"
Write-Host $parsed.highestEducationLevel


Write-Host ""
Write-Host "Target job titles:"

if (
    $null -eq $parsed.targetJobTitles `
    -or $parsed.targetJobTitles.Count -eq 0
) {
    Write-Host "(none)"
}
else {
    $parsed.targetJobTitles |
        ForEach-Object {
            Write-Host " - $_"
        }
}


Write-Host ""
Write-Host "Preferred locations:"

if (
    $null -eq $parsed.preferredLocations `
    -or $parsed.preferredLocations.Count -eq 0
) {
    Write-Host "(none)"
}
else {
    $parsed.preferredLocations |
        ForEach-Object {
            Write-Host " - $_"
        }
}


Write-Host ""
Write-Host "Preferred work modes:"

if (
    $null -eq $parsed.preferredWorkModes `
    -or $parsed.preferredWorkModes.Count -eq 0
) {
    Write-Host "(none)"
}
else {
    $parsed.preferredWorkModes |
        ForEach-Object {
            Write-Host " - $_"
        }
}


Write-Host ""
Write-Host "Recent job titles:"

if (
    $null -eq $parsed.recentJobTitles `
    -or $parsed.recentJobTitles.Count -eq 0
) {
    Write-Host "(none)"
}
else {
    $parsed.recentJobTitles |
        ForEach-Object {
            Write-Host " - $_"
        }
}


Write-Host ""
Write-Host "Skills:"

if (
    $null -eq $parsed.skills `
    -or $parsed.skills.Count -eq 0
) {
    Write-Host "(none)"
}
else {
    $parsed.skills |
        Select-Object `
            name,
            normalizedName,
            category,
            proficiency |
        Format-Table -AutoSize
}


Write-Host ""
Write-Host "Work experiences:"

if (
    $null -eq $parsed.workExperiences `
    -or $parsed.workExperiences.Count -eq 0
) {
    Write-Host "(none)"
}
else {
    $parsed.workExperiences |
        Select-Object `
            jobTitle,
            normalizedJobTitle,
            companyName,
            employmentType,
            location,
            workMode,
            startDate,
            endDate,
            current |
        Format-Table -AutoSize
}


Write-Host ""
Write-Host "Parser version:"
Write-Host $parsed.parserVersion


Write-Host ""
Write-Host "Parser warnings:"

if (
    $null -eq $parsed.parserWarnings `
    -or $parsed.parserWarnings.Count -eq 0
) {
    Write-Host "(none)"
}
else {
    $parsed.parserWarnings |
        ForEach-Object {
            Write-Host " - $_"
        }
}


Write-Host ""
Write-Host "========================================"
Write-Host "7. READ CANDIDATE PROFILE FROM MONGO"
Write-Host "========================================"


$profileEval = "const d = db.candidate_profiles.findOne({ rawCvId: '$rawCvId' }); if (!d) { print('null'); } else { print(EJSON.stringify(d, null, 2)); }"


$profileMongoJson = & docker exec `
    autojob-mongo `
    mongosh `
    --quiet `
    -u root `
    -p password `
    --authenticationDatabase admin `
    autojob `
    --eval "$profileEval"


if ($LASTEXITCODE -ne 0) {
    throw "Failed to read candidate_profiles from Mongo"
}


$profileMongoFile = Join-Path `
    $OutputDir `
    "candidate-profile-mongo.json"


$profileMongoJson |
    Set-Content `
        -Path $profileMongoFile `
        -Encoding UTF8


Write-Host ""
Write-Host "Mongo candidate profile:"
Write-Host $profileMongoJson


Write-Host ""
Write-Host "========================================"
Write-Host "8. READ CANDIDATE EMBEDDING FROM MONGO"
Write-Host "========================================"


$embeddingEval = "const d = db.candidate_embeddings.findOne({ rawCvId: '$rawCvId' }, null, { sort: { updatedAt: -1 } }); if (!d) { print('null'); } else { print(EJSON.stringify(d, null, 2)); }"


$embeddingMongoJson = & docker exec `
    autojob-mongo `
    mongosh `
    --quiet `
    -u root `
    -p password `
    --authenticationDatabase admin `
    autojob `
    --eval "$embeddingEval"


if ($LASTEXITCODE -ne 0) {
    throw "Failed to read candidate_embeddings from Mongo"
}


if ([string]::IsNullOrWhiteSpace($embeddingMongoJson)) {
    Write-Host ""
    Write-Host "NO CANDIDATE EMBEDDING OUTPUT"

    exit 2
}


if ($embeddingMongoJson.Trim() -eq "null") {
    Write-Host ""
    Write-Host "NO CANDIDATE EMBEDDING FOUND"
    Write-Host ""
    Write-Host "Check:"
    Write-Host "docker compose --env-file .env logs --tail=200 autojob-app"
    Write-Host "docker compose --env-file .env logs --tail=200 embedding-service"

    exit 2
}


$embeddingMongoFile = Join-Path `
    $OutputDir `
    "candidate-embedding-mongo.json"


$embeddingMongoJson |
    Set-Content `
        -Path $embeddingMongoFile `
        -Encoding UTF8


try {
    $embedding = (
        $embeddingMongoJson |
        ConvertFrom-Json
    )
}
catch {
    Write-Host $embeddingMongoJson

    throw "Candidate embedding Mongo output is not valid JSON"
}


Write-Host ""
Write-Host "========================================"
Write-Host "9. EMBEDDING METADATA"
Write-Host "========================================"


Write-Host ""
Write-Host "Mongo id:"
Write-Host $embedding._id.'$oid'


Write-Host ""
Write-Host "candidateProfileId:"
Write-Host $embedding.candidateProfileId


Write-Host ""
Write-Host "rawCvId:"
Write-Host $embedding.rawCvId


Write-Host ""
Write-Host "status:"
Write-Host $embedding.status


Write-Host ""
Write-Host "parserVersion:"
Write-Host $embedding.parserVersion


Write-Host ""
Write-Host "textVersion:"
Write-Host $embedding.textVersion


Write-Host ""
Write-Host "modelName:"
Write-Host $embedding.modelName


Write-Host ""
Write-Host "modelRevision:"
Write-Host $embedding.modelRevision


Write-Host ""
Write-Host "embeddingVersion:"
Write-Host $embedding.embeddingVersion


Write-Host ""
Write-Host "textHash:"
Write-Host $embedding.textHash


Write-Host ""
Write-Host "dimension:"
Write-Host $embedding.dimension


Write-Host ""
Write-Host "normalized:"
Write-Host $embedding.normalized


Write-Host ""
Write-Host "lastError:"
Write-Host $embedding.lastError


Write-Host ""
Write-Host "========================================"
Write-Host "10. EMBEDDING VECTOR"
Write-Host "========================================"


$vectorCount = 0


if ($null -ne $embedding.vector) {
    $vectorCount = $embedding.vector.Count
}


Write-Host ""
Write-Host "Vector length:"
Write-Host $vectorCount


Write-Host ""
Write-Host "First 20 dimensions:"


if ($vectorCount -gt 0) {
    $embedding.vector |
        Select-Object -First 20
}
else {
    Write-Host "(vector empty)"
}


Write-Host ""
Write-Host "========================================"
Write-Host "11. VERIFY EMBEDDING"
Write-Host "========================================"


if ($embedding.status -eq "READY") {
    Write-Host "[PASS] embedding status = READY"
}
else {
    Write-Host "[FAIL] embedding status = $($embedding.status)"
}


if ($embedding.textVersion -eq "candidate-text-v1") {
    Write-Host "[PASS] textVersion = candidate-text-v1"
}
else {
    Write-Host "[WARN] textVersion = $($embedding.textVersion)"
}


if ($embedding.dimension -eq 384) {
    Write-Host "[PASS] dimension metadata = 384"
}
else {
    Write-Host "[WARN] dimension metadata = $($embedding.dimension)"
}


if ($vectorCount -eq $embedding.dimension) {
    Write-Host "[PASS] vector length matches dimension"
}
else {
    Write-Host "[FAIL] vector length does not match dimension"
}


if ($embedding.normalized -eq $true) {
    Write-Host "[PASS] vector normalized = true"
}
else {
    Write-Host "[WARN] vector normalized = $($embedding.normalized)"
}


Write-Host ""
Write-Host "========================================"
Write-Host "12. OUTPUT FILES"
Write-Host "========================================"


Write-Host ""
Write-Host "Parsed API response:"
Write-Host $parsedFile


Write-Host ""
Write-Host "Candidate profile Mongo document:"
Write-Host $profileMongoFile


Write-Host ""
Write-Host "Candidate embedding Mongo document:"
Write-Host $embeddingMongoFile


Write-Host ""
Write-Host "DONE"