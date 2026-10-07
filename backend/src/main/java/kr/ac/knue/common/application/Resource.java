package kr.ac.knue.common.application;

/** SQL identifiers are closed, trusted declarations, never supplied by an HTTP caller. */
public enum Resource {
    ACCOUNTS("user_account", "account_id",
        "login_id:string personnel_id:long use_status:string"),
    ROLES("role", "role_code",
        "role_name:string purpose:string assignment_criteria:string default_data_scope:string use_status:string"),
    USER_ROLES("user_role", "assignment_id",
        "role_code:role approver_id:long valid_start:date valid_end:date assignment_source:string"),
    PERMISSIONS("menu_permission", "permission_id",
        "menu_id:long role_code:role organization_id:long account_id:long access_allowed:boolean"),
    MENUS("menu", "menu_id",
        "menu_name:string screen_id:string url:string icon:string business_category:string "
        + "description:string use_status:string"),
    GROUPS("code_group", "code_group_id",
        "group_id:string group_name:string description:string managing_organization_id:long use_status:string"),
    CODES("detail_code", "detail_code_id",
        "code_group_id:long code_value:string code_name:string parent_detail_code_id:long display_order:int "
        + "additional_attributes:object use_status:string valid_start:date valid_end:date"),
    ORGANIZATIONS("organization", "organization_id", ""),
    PERSONNEL("korus_personnel_snapshot", "personnel_id", ""),
    POSITIONS("organization_user_mapping", "mapping_id", "");

    private final String table;
    private final String key;
    private final String input;

    Resource(String table, String key, String input) {
        this.table = table;
        this.key = key;
        this.input = input;
    }

    /** Returns a closed trusted SQL table identifier, never an HTTP-provided string. */
    public String getTable() {
        return table;
    }

    /** Returns the declared stable key used by persistence lookup/update. */
    public String getKey() {
        return key;
    }

    /** Declares editable wire fields only, excluding source-owned and server metadata columns. */
    public String input() {
        return input;
    }
}
