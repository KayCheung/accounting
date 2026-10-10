// 文件路径：src/views/config/rule/utils/exampleCodeGenerator.ts
import type { RuleResponse } from '@/api/rule'

export interface ExampleConfig {
  host: string
  amount: number
  traceNo?: string
  tradeTime?: string
  origFreezeNo?: string
}

export interface GeneratedCodeSet {
  curl: string
  json: string
  java: string
  javascript: string
  go: string
  python: string
}

export interface GeneratedRuleExample {
  rule: RuleResponse
  isPreFreeze: boolean
  submitUrl: string
  freezeUrl: string
  unfreezeUrl: string
  submitPayload: Record<string, any>
  freezePayload: Record<string, any>
  unfreezePayload: Record<string, any>
  submitCodes: GeneratedCodeSet
  freezeCodes: GeneratedCodeSet
  unfreezeCodes: GeneratedCodeSet
}

/**
 * 格式化当前日期时间为 YYYY-MM-DDTHH:mm:ss
 */
export function getFormattedTradeTime(): string {
  const now = new Date()
  const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`)
  const yyyy = now.getFullYear()
  const mm = pad(now.getMonth() + 1)
  const dd = pad(now.getDate())
  const hh = pad(now.getHours())
  const min = pad(now.getMinutes())
  const ss = pad(now.getSeconds())
  return `${yyyy}-${mm}-${dd}T${hh}:${min}:${ss}`
}

/**
 * 生成样例跟踪号 TRYYYYMMDDHHmmss001
 */
export function getSampleTraceNo(prefix = 'TR'): string {
  const now = new Date()
  const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`)
  const yyyy = now.getFullYear()
  const mm = pad(now.getMonth() + 1)
  const dd = pad(now.getDate())
  const hh = pad(now.getHours())
  const min = pad(now.getMinutes())
  const ss = pad(now.getSeconds())
  return `${prefix}${yyyy}${mm}${dd}${hh}${min}${ss}001`
}

/**
 * 根据规则 entries 提取去重后的款项类型并构造 details
 */
export function buildSampleDetails(rule: RuleResponse, totalAmount: number) {
  const fundsTypes: string[] = []
  if (rule.entries && rule.entries.length > 0) {
    for (const entry of rule.entries) {
      if (entry.fundsType && !fundsTypes.includes(entry.fundsType)) {
        fundsTypes.push(entry.fundsType)
      }
    }
  }

  if (fundsTypes.length === 0) {
    fundsTypes.push('PRINCIPAL')
  }

  if (fundsTypes.length === 1) {
    return [
      {
        customerId: 'CUST_10001',
        customerType: 1,
        fundsType: fundsTypes[0],
        itemCode: 'ITEM_001',
        amount: Number(totalAmount.toFixed(2))
      }
    ]
  }

  // 多个款项类型按比例均分
  const partAmount = Number((totalAmount / fundsTypes.length).toFixed(2))
  let accumulated = 0
  return fundsTypes.map((ft, index) => {
    let itemAmt = partAmount
    if (index === fundsTypes.length - 1) {
      itemAmt = Number((totalAmount - accumulated).toFixed(2))
    } else {
      accumulated += partAmount
    }
    return {
      customerId: `CUST_${10001 + index}`,
      customerType: 1,
      fundsType: ft,
      itemCode: `ITEM_${index + 1}`,
      amount: itemAmt
    }
  })
}

/**
 * 生成各语言代码示例集合
 */
export function generateCodeSet(
  url: string,
  method: 'POST' | 'GET',
  payload: Record<string, any>,
  actionDesc: string
): GeneratedCodeSet {
  const jsonString = JSON.stringify(payload, null, 2)

  // 1. cURL
  const curl = `# ${actionDesc}\ncurl -X ${method} "${url}" \\\n  -H "Content-Type: application/json" \\\n  -d '${jsonString}'`

  // 2. JSON
  const json = jsonString

  // 3. Java (HttpClient 原生标准库，无三方依赖)
  const java = `// 文件：AccountingClient.java
// 依赖：Java 11+ 原生 java.net.http.HttpClient（无需引入额外三方库）
package com.example.accounting.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AccountingClient {

    private static final String API_URL = "${url}";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * ${actionDesc}
     */
    public static String executeRequest() throws Exception {
        String jsonPayload = """
${jsonString.replace(/^/gm, '                ')}
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("HTTP 状态码: " + response.statusCode());
        System.out.println("响应内容: " + response.body());
        return response.body();
    }

    public static void main(String[] args) {
        try {
            executeRequest();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}`

  // 4. JavaScript / TypeScript
  const javascript = `/**
 * ${actionDesc}
 * 运行环境：Node.js 18+ 或 现代浏览器 fetch API
 */
async function callAccountingApi() {
  const apiUrl = '${url}'
  const payload = ${jsonString}

  try {
    const response = await fetch(apiUrl, {
      method: '${method}',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    })

    if (!response.ok) {
      throw new Error(\`HTTP 错误! 状态码: \${response.status}\`)
    }

    const result = await response.json()
    console.log('账务接口返回结果:', result)
    return result
  } catch (error) {
    console.error('调用账务接口失败:', error)
    throw error
  }
}

// 执行调用
callAccountingApi()`

  // 5. Go (net/http 标准库)
  const go = `// main.go
// 运行命令：go run main.go
package main

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
	"time"
)

// ${actionDesc}
func main() {
	apiURL := "${url}"

	// 请求载荷 JSON
	jsonPayload := []byte(\`${jsonString}\`)

	req, err := http.NewRequest("${method}", apiURL, bytes.NewBuffer(jsonPayload))
	if err != nil {
		fmt.Printf("创建请求失败: %v\\n", err)
		return
	}

	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{
		Timeout: 10 * time.Second,
	}

	resp, err := client.Do(req)
	if err != nil {
		fmt.Printf("发送请求失败: %v\\n", err)
		return
	}
	defer resp.Body.Close()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		fmt.Printf("读取响应失败: %v\\n", err)
		return
	}

	fmt.Printf("HTTP 状态码: %d\\n", resp.StatusCode)
	fmt.Printf("响应内容: %s\\n", string(bodyBytes))
}`

  // 6. Python (requests 库)
  const python = `# -*- coding: utf-8 -*-
# 依赖：pip install requests
import requests
import json

API_URL = "${url}"

# 请求体数据
payload = ${jsonString}

headers = {
    "Content-Type": "application/json"
}

try:
    # ${actionDesc}
    response = requests.post(
        API_URL,
        json=payload,
        headers=headers,
        timeout=10
    )
    print(f"HTTP 状态码: {response.status_code}")
    print("响应内容:", response.text)
except requests.exceptions.RequestException as e:
    print(f"请求发生异常: {e}")`

  return {
    curl,
    json,
    java,
    javascript,
    go,
    python
  }
}

/**
 * 根据记账规则与配置，生成完整的调用示例
 */
export function generateRuleExample(rule: RuleResponse, config: ExampleConfig): GeneratedRuleExample {
  const host = config.host.replace(/\/+$/, '')
  const isPreFreeze = rule.requirePreFreeze === 1
  const totalAmount = config.amount || 1000.0
  const tradeTime = config.tradeTime || getFormattedTradeTime()
  const traceNo = config.traceNo || getSampleTraceNo('TR')
  const freezeTraceNo = getSampleTraceNo('TR_FR_')
  const sampleFreezeNo = config.origFreezeNo || 'FR2026101000123'

  const details = buildSampleDetails(rule, totalAmount)

  // 1. 记账流水提交 Payload (/accounting/journal/submit)
  const submitPayload: Record<string, any> = {
    traceNo: traceNo,
    traceSeq: 0,
    businessCode: rule.businessCode,
    tradingCode: rule.tradingCode,
    payChannel: rule.payChannel,
    tradeType: 1, // 1-正常记账
    amount: totalAmount,
    tradeTime: tradeTime,
    summary: `${rule.ruleName || '业务记账'}`,
    ...(isPreFreeze ? { origFreezeNo: sampleFreezeNo } : {}),
    details: details
  }

  // 2. 预冻结 Payload (/accounting/journal/freeze)
  const freezePayload: Record<string, any> = {
    traceNo: freezeTraceNo,
    traceSeq: 0,
    businessCode: rule.businessCode,
    tradingCode: rule.tradingCode,
    payChannel: rule.payChannel,
    amount: totalAmount,
    tradeTime: tradeTime,
    summary: `${rule.ruleName || '业务交易'}预冻结`,
    details: details
  }

  // 3. 解冻撤销 Payload (/accounting/journal/unfreeze)
  const unfreezePayload: Record<string, any> = {
    origTraceNo: freezeTraceNo,
    reason: '业务超时或用户取消交易'
  }

  const submitUrl = `${host}/accounting/journal/submit`
  const freezeUrl = `${host}/accounting/journal/freeze`
  const unfreezeUrl = `${host}/accounting/journal/unfreeze`

  const submitCodes = generateCodeSet(
    submitUrl,
    'POST',
    submitPayload,
    isPreFreeze ? '第2步：预冻结核销记账入账' : '直接记账流水提交'
  )

  const freezeCodes = generateCodeSet(
    freezeUrl,
    'POST',
    freezePayload,
    '第1步：业务预冻结出金资金'
  )

  const unfreezeCodes = generateCodeSet(
    unfreezeUrl,
    'POST',
    unfreezePayload,
    '异常撤销：全额解冻预冻结资金'
  )

  return {
    rule,
    isPreFreeze,
    submitUrl,
    freezeUrl,
    unfreezeUrl,
    submitPayload,
    freezePayload,
    unfreezePayload,
    submitCodes,
    freezeCodes,
    unfreezeCodes
  }
}
