package com.diffusiondesk.desktop.core

internal const val IDEOGRAM4_JSON_SCHEMA = """
{
  "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "Ideogram 4 structured caption",
  "type": "object",
  "additionalProperties": false,
  "required": ["high_level_description", "style_description", "compositional_deconstruction"],
  "properties": {
    "high_level_description": {"type": "string", "minLength": 1},
    "style_description": {
      "type": "object",
      "additionalProperties": false,
      "required": ["aesthetics", "lighting", "medium"],
      "properties": {
        "aesthetics": {"type": "string", "minLength": 1},
        "lighting": {"type": "string", "minLength": 1},
        "medium": {"type": "string", "minLength": 1},
        "photo": {"type": "string", "minLength": 1},
        "art_style": {"type": "string", "minLength": 1},
        "color_palette": {"${'$'}ref": "#/${'$'}defs/globalPalette"}
      },
      "oneOf": [
        {"required": ["photo"], "not": {"required": ["art_style"]}},
        {"required": ["art_style"], "not": {"required": ["photo"]}}
      ]
    },
    "compositional_deconstruction": {
      "type": "object",
      "additionalProperties": false,
      "required": ["background", "elements"],
      "properties": {
        "background": {"type": "string", "minLength": 1},
        "elements": {
          "type": "array",
          "minItems": 1,
          "items": {
            "oneOf": [
              {"${'$'}ref": "#/${'$'}defs/objectElement"},
              {"${'$'}ref": "#/${'$'}defs/textElement"}
            ]
          }
        }
      }
    }
  },
  "${'$'}defs": {
    "bbox": {
      "type": "array",
      "prefixItems": [
        {"type": "integer", "minimum": 0, "maximum": 1000},
        {"type": "integer", "minimum": 0, "maximum": 1000},
        {"type": "integer", "minimum": 0, "maximum": 1000},
        {"type": "integer", "minimum": 0, "maximum": 1000}
      ],
      "minItems": 4,
      "maxItems": 4
    },
    "globalPalette": {
      "type": "array",
      "maxItems": 16,
      "items": {"type": "string", "pattern": "^#[0-9A-F]{6}$"}
    },
    "elementPalette": {
      "type": "array",
      "maxItems": 5,
      "items": {"type": "string", "pattern": "^#[0-9A-F]{6}$"}
    },
    "objectElement": {
      "type": "object",
      "additionalProperties": false,
      "required": ["type", "desc"],
      "properties": {
        "type": {"const": "obj"},
        "bbox": {"${'$'}ref": "#/${'$'}defs/bbox"},
        "desc": {"type": "string", "minLength": 1},
        "color_palette": {"${'$'}ref": "#/${'$'}defs/elementPalette"}
      }
    },
    "textElement": {
      "type": "object",
      "additionalProperties": false,
      "required": ["type", "text", "desc"],
      "properties": {
        "type": {"const": "text"},
        "bbox": {"${'$'}ref": "#/${'$'}defs/bbox"},
        "text": {"type": "string", "minLength": 1},
        "desc": {"type": "string", "minLength": 1},
        "color_palette": {"${'$'}ref": "#/${'$'}defs/elementPalette"}
      }
    }
  }
}
"""

internal const val IDEOGRAM4_SCHEMA_INSTRUCTION = """
The final caption must conform to this Ideogram 4 JSON Schema:
<ideogram4_json_schema>
$IDEOGRAM4_JSON_SCHEMA
</ideogram4_json_schema>

Semantic constraints not expressible in the schema: bbox is [y_min, x_min, y_max, x_max], with y_min < y_max and x_min < x_max. Preserve literal text exactly. The medium field is distinct from the mutually exclusive photo and art_style fields.
"""
