class Client {
  final String id;
  final String mfiMemberNo;
  final String fullName;
  final String phone;
  final String status;

  Client({
    required this.id,
    required this.mfiMemberNo,
    required this.fullName,
    required this.phone,
    required this.status,
  });

  factory Client.fromJson(Map<String, dynamic> json) => Client(
        id: json['id'] as String,
        mfiMemberNo: json['mfiMemberNo'] as String,
        fullName: json['fullName'] as String,
        phone: json['phone'] as String? ?? '',
        status: json['status'] as String,
      );
}
