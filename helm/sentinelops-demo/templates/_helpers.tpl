{{/* Common labels applied to every object in the chart. */}}
{{- define "demo.labels" -}}
app.kubernetes.io/part-of: sentinelops-demo
app.kubernetes.io/managed-by: {{ .Release.Service }}
sentinelops.io/release: {{ .Release.Name }}
{{- end -}}
