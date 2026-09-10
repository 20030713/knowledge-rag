param(
    [Parameter(Mandatory = $true)]
    [string]$Token,

    [string]$BaseUrl = "http://localhost:8080",

    [string[]]$ShareWith = @(),

    [switch]$RunEvaluation
)

$ErrorActionPreference = "Stop"
$headers = @{ Authorization = "Bearer $Token" }
$corpusRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\docs\evaluation-corpus")).Path
$knowledgeBaseName = "RAG 回归评测基线"

function Invoke-RagApi {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body
    )
    $parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
        ContentType = "application/json; charset=utf-8"
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 8
    }
    return Invoke-RestMethod @parameters
}

$knowledgeBases = (Invoke-RagApi -Method Get -Path "/api/kb" -Body $null).data
$knowledgeBase = $knowledgeBases | Where-Object { $_.name -eq $knowledgeBaseName } | Select-Object -First 1
if ($null -eq $knowledgeBase) {
    $knowledgeBase = (Invoke-RagApi -Method Post -Path "/api/kb" -Body @{
        name = $knowledgeBaseName
        description = "自动化 RAG 质量回归语料，不包含真实业务数据"
    }).data
}

$existingDocuments = (Invoke-RagApi -Method Get -Path "/api/doc?kbId=$($knowledgeBase.id)" -Body $null).data
$documentNames = @("expense-policy.md", "incident-runbook.md", "rag-engineering-guide.md")
foreach ($documentName in $documentNames) {
    if ($existingDocuments.fileName -contains $documentName) {
        continue
    }
    $uploadParameters = @{
        Method = "Post"
        Uri = "$BaseUrl/api/doc/upload?kbId=$($knowledgeBase.id)"
        Headers = $headers
        Form = @{ file = Get-Item (Join-Path $corpusRoot $documentName) }
    }
    $upload = Invoke-RestMethod @uploadParameters
    Invoke-RagApi -Method Post -Path "/api/doc/$($upload.data.id)/parse" -Body $null | Out-Null
}

$deadline = (Get-Date).AddMinutes(5)
do {
    $documents = (Invoke-RagApi -Method Get -Path "/api/doc?kbId=$($knowledgeBase.id)" -Body $null).data
    $pending = @($documents | Where-Object { $_.status -notin @("COMPLETED", "FAILED") })
    if ($pending.Count -gt 0) {
        Start-Sleep -Seconds 2
    }
} while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline)

$failed = @($documents | Where-Object { $_.status -eq "FAILED" })
if ($failed.Count -gt 0) {
    throw "评测文档解析失败：$($failed.fileName -join ', ')"
}
if ($pending.Count -gt 0) {
    throw "等待评测文档解析超时"
}

$existingCases = (Invoke-RagApi -Method Get -Path "/api/rag/evals/cases?kbId=$($knowledgeBase.id)" -Body $null).data
$cases = Get-Content -Raw (Join-Path $corpusRoot "eval-cases.json") | ConvertFrom-Json
foreach ($case in $cases) {
    $payload = @{
        question = $case.question
        expectedAnswer = $case.expectedAnswer
        expectedKeywords = $case.expectedKeywords
        expectedSource = $case.expectedSource
        expectNoAnswer = $case.expectNoAnswer
        enabled = $true
    }
    $existingCase = $existingCases | Where-Object { $_.question -eq $case.question } | Select-Object -First 1
    if ($null -eq $existingCase) {
        Invoke-RagApi -Method Post -Path "/api/rag/evals/cases?kbId=$($knowledgeBase.id)" -Body $payload | Out-Null
    } else {
        Invoke-RagApi -Method Put -Path "/api/rag/evals/cases/$($existingCase.id)" -Body $payload | Out-Null
    }
}

$members = (Invoke-RagApi -Method Get -Path "/api/kb/$($knowledgeBase.id)/members" -Body $null).data
foreach ($username in $ShareWith) {
    if ($members.username -contains $username) {
        continue
    }
    Invoke-RagApi -Method Post -Path "/api/kb/$($knowledgeBase.id)/members" -Body @{
        username = $username
        role = "ADMIN"
    } | Out-Null
}

$result = [ordered]@{
    knowledgeBaseId = $knowledgeBase.id
    knowledgeBaseName = $knowledgeBase.name
    documents = $documents.Count
    chunks = ($documents | Measure-Object -Property chunkCount -Sum).Sum
    evaluationCases = $cases.Count
}
if ($RunEvaluation) {
    $result.evaluation = (Invoke-RagApi -Method Post -Path "/api/rag/evals/run?kbId=$($knowledgeBase.id)" -Body $null).data
}
$result | ConvertTo-Json -Depth 10
