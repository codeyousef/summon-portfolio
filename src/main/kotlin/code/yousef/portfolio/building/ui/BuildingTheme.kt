package code.yousef.portfolio.building.ui

/**
 * Theme configuration for Arabic RTL building management UI
 */
object BuildingTheme {
    object Colors {
        // Primary colors
        const val PRIMARY = "#1a73e8"
        const val PRIMARY_DARK = "#1557b0"
        const val PRIMARY_LIGHT = "#4285f4"
        
        // Background
        const val BG_PRIMARY = "#f8fafc"
        const val BG_SECONDARY = "#ffffff"
        const val BG_CARD = "#ffffff"
        const val BG_HOVER = "#f1f5f9"
        
        // Text
        const val TEXT_PRIMARY = "#1e293b"
        const val TEXT_SECONDARY = "#64748b"
        const val TEXT_MUTED = "#94a3b8"
        const val TEXT_WHITE = "#ffffff"
        
        // Status colors
        const val SUCCESS = "#22c55e"
        const val SUCCESS_BG = "#dcfce7"
        const val SUCCESS_TEXT = "#166534"
        
        const val WARNING = "#f59e0b"
        const val WARNING_BG = "#fef3c7"
        const val WARNING_TEXT = "#92400e"
        
        const val DANGER = "#ef4444"
        const val DANGER_BG = "#fee2e2"
        const val DANGER_TEXT = "#991b1b"
        
        const val INFO = "#3b82f6"
        const val INFO_BG = "#dbeafe"
        const val INFO_TEXT = "#1e40af"
        
        // Border
        const val BORDER = "#e2e8f0"
        const val BORDER_FOCUS = "#1a73e8"
        
        // Shadow
        const val SHADOW = "rgba(0, 0, 0, 0.1)"
    }
    
    object Spacing {
        const val xs = "4px"
        const val sm = "8px"
        const val md = "16px"
        const val lg = "24px"
        const val xl = "32px"
        const val xxl = "48px"
    }
    
    object FontSize {
        const val xs = "12px"
        const val sm = "14px"
        const val base = "16px"
        const val lg = "18px"
        const val xl = "20px"
        const val xxl = "24px"
        const val xxxl = "30px"
    }
    
    object BorderRadius {
        const val sm = "4px"
        const val md = "8px"
        const val lg = "12px"
        const val xl = "16px"
        const val full = "9999px"
    }
    
    // Common CSS for RTL Arabic pages
    val globalStyles = """
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }
        
        body {
            font-family: 'Tajawal', 'Segoe UI', Tahoma, sans-serif;
            background-color: ${Colors.BG_PRIMARY};
            color: ${Colors.TEXT_PRIMARY};
            direction: rtl;
            text-align: right;
            line-height: 1.6;
        }
        
        a {
            color: ${Colors.PRIMARY};
            text-decoration: none;
        }
        
        a:hover {
            text-decoration: underline;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 0 ${Spacing.md};
        }
        
        .card {
            background: ${Colors.BG_CARD};
            border-radius: ${BorderRadius.lg};
            box-shadow: 0 1px 3px ${Colors.SHADOW};
            padding: ${Spacing.lg};
            margin-bottom: ${Spacing.md};
        }
        
        .card-header {
            font-size: ${FontSize.lg};
            font-weight: 600;
            margin-bottom: ${Spacing.md};
            color: ${Colors.TEXT_PRIMARY};
        }
        
        .btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            padding: ${Spacing.sm} ${Spacing.md};
            border-radius: ${BorderRadius.md};
            font-size: ${FontSize.sm};
            font-weight: 500;
            cursor: pointer;
            border: none;
            transition: all 0.2s ease;
        }
        
        .btn-primary {
            background: ${Colors.PRIMARY};
            color: ${Colors.TEXT_WHITE};
        }
        
        .btn-primary:hover {
            background: ${Colors.PRIMARY_DARK};
        }
        
        .btn-secondary {
            background: ${Colors.BG_HOVER};
            color: ${Colors.TEXT_PRIMARY};
        }
        
        .btn-secondary:hover {
            background: ${Colors.BORDER};
        }
        
        .btn-danger {
            background: ${Colors.DANGER};
            color: ${Colors.TEXT_WHITE};
        }
        
        .btn-danger:hover {
            background: #dc2626;
        }
        
        .btn-success {
            background: ${Colors.SUCCESS};
            color: ${Colors.TEXT_WHITE};
        }
        
        .btn-success:hover {
            background: #16a34a;
        }
        
        .input {
            width: 100%;
            padding: ${Spacing.sm} ${Spacing.md};
            border: 1px solid ${Colors.BORDER};
            border-radius: ${BorderRadius.md};
            font-size: ${FontSize.base};
            direction: rtl;
            text-align: right;
        }
        
        .input:focus {
            outline: none;
            border-color: ${Colors.BORDER_FOCUS};
            box-shadow: 0 0 0 3px rgba(26, 115, 232, 0.1);
        }
        
        .label {
            display: block;
            margin-bottom: ${Spacing.xs};
            font-size: ${FontSize.sm};
            font-weight: 500;
            color: ${Colors.TEXT_SECONDARY};
        }
        
        .form-group {
            margin-bottom: ${Spacing.md};
        }
        
        .badge {
            display: inline-flex;
            align-items: center;
            padding: ${Spacing.xs} ${Spacing.sm};
            border-radius: ${BorderRadius.full};
            font-size: ${FontSize.xs};
            font-weight: 500;
        }
        
        .badge-success {
            background: ${Colors.SUCCESS_BG};
            color: ${Colors.SUCCESS_TEXT};
        }
        
        .badge-warning {
            background: ${Colors.WARNING_BG};
            color: ${Colors.WARNING_TEXT};
        }
        
        .badge-danger {
            background: ${Colors.DANGER_BG};
            color: ${Colors.DANGER_TEXT};
        }
        
        .badge-info {
            background: ${Colors.INFO_BG};
            color: ${Colors.INFO_TEXT};
        }
        
        table {
            width: 100%;
            border-collapse: collapse;
        }
        
        th, td {
            padding: ${Spacing.sm} ${Spacing.md};
            text-align: right;
            border-bottom: 1px solid ${Colors.BORDER};
        }
        
        th {
            background: ${Colors.BG_HOVER};
            font-weight: 600;
            font-size: ${FontSize.sm};
            color: ${Colors.TEXT_SECONDARY};
        }
        
        tr:hover {
            background: ${Colors.BG_HOVER};
        }
        
        .stat-card {
            background: ${Colors.BG_CARD};
            border-radius: ${BorderRadius.lg};
            padding: ${Spacing.lg};
            text-align: center;
        }
        
        .stat-value {
            font-size: ${FontSize.xxxl};
            font-weight: 700;
            color: ${Colors.PRIMARY};
            margin-bottom: ${Spacing.xs};
        }
        
        .stat-label {
            font-size: ${FontSize.sm};
            color: ${Colors.TEXT_SECONDARY};
        }
        
        .nav {
            background: ${Colors.BG_CARD};
            padding: ${Spacing.md} ${Spacing.lg};
            box-shadow: 0 1px 3px ${Colors.SHADOW};
            margin-bottom: ${Spacing.lg};
        }
        
        .nav-content {
            display: flex;
            justify-content: space-between;
            align-items: center;
            max-width: 1200px;
            margin: 0 auto;
        }
        
        .nav-title {
            font-size: ${FontSize.xl};
            font-weight: 700;
            color: ${Colors.PRIMARY};
        }
        
        .nav-links {
            display: flex;
            gap: ${Spacing.md};
            align-items: center;
        }
        
        .nav-link {
            color: ${Colors.TEXT_SECONDARY};
            font-size: ${FontSize.sm};
            padding: ${Spacing.sm} ${Spacing.md};
            border-radius: ${BorderRadius.md};
            transition: all 0.2s ease;
        }
        
        .nav-link:hover, .nav-link.active {
            color: ${Colors.PRIMARY};
            background: ${Colors.BG_HOVER};
            text-decoration: none;
        }
        
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: ${Spacing.md};
            margin-bottom: ${Spacing.lg};
        }
        
        .alert {
            padding: ${Spacing.md};
            border-radius: ${BorderRadius.md};
            margin-bottom: ${Spacing.md};
        }
        
        .alert-error {
            background: ${Colors.DANGER_BG};
            color: ${Colors.DANGER_TEXT};
            border: 1px solid ${Colors.DANGER};
        }
        
        .alert-success {
            background: ${Colors.SUCCESS_BG};
            color: ${Colors.SUCCESS_TEXT};
            border: 1px solid ${Colors.SUCCESS};
        }
        
        .empty-state {
            text-align: center;
            padding: ${Spacing.xxl};
            color: ${Colors.TEXT_MUTED};
        }
        
        .page-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: ${Spacing.lg};
        }
        
        .page-title {
            font-size: ${FontSize.xxl};
            font-weight: 700;
            color: ${Colors.TEXT_PRIMARY};
        }
    """.trimIndent()
}
